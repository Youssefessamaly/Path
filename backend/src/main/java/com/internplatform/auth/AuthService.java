package com.internplatform.auth;

import com.internplatform.ats.access.AtsMemberRole;
import com.internplatform.ats.access.CompanyMember;
import com.internplatform.ats.access.CompanyMemberRepository;
import com.internplatform.ats.access.MemberStatus;
import com.internplatform.companyprofile.CompanyProfile;
import com.internplatform.companyprofile.CompanyProfileRepository;
import com.internplatform.security.JwtService;
import com.internplatform.user.Role;
import com.internplatform.user.User;
import com.internplatform.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            CompanyProfileRepository companyProfileRepository,
            CompanyMemberRepository companyMemberRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.companyMemberRepository = companyMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public RegisterResponse registerIntern(RegisterRequest request) {
        return register(request, Role.INTERN);
    }

    /**
     * A COMPANY registration also creates its CompanyProfile row immediately (PENDING by the
     * entity's own default — see CompanyProfile) rather than waiting for onboarding to start
     * it, as CompanyProfileService.upsertProfile otherwise would. This closes the only window
     * where a company could exist without an approval record: the approval status must be
     * checkable (and the company blocked from marketplace features) from the moment the
     * account exists, not only once it gets around to onboarding. Also creates the matching
     * ats_company_members OWNER/ACTIVE row (see CompanyAccessResolver) so this company has a
     * complete membership record from day one, same as every pre-existing company got via the
     * V17 backfill — CompanyAccessResolver does not actually depend on this row for owner
     * resolution (company_profiles.user_id is always authoritative), but future team-management
     * features (listing a company's members) do. Wrapped in one transaction so a company user
     * is never persisted without its profile and membership row, or vice versa.
     */
    @Transactional
    public RegisterResponse registerCompany(RegisterRequest request) {
        RegisterResponse response = register(request, Role.COMPANY);
        CompanyProfile profile = companyProfileRepository.save(new CompanyProfile(response.id()));
        companyMemberRepository.save(
                new CompanyMember(profile.getId(), response.id(), AtsMemberRole.OWNER, MemberStatus.ACTIVE));
        return response;
    }

    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        if (!user.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is disabled"
            );
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }

    private RegisterResponse register(RegisterRequest request, Role role) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists"
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                normalizedEmail,
                passwordHash,
                role
        );

        try {
            User savedUser = userRepository.saveAndFlush(user);

            return new RegisterResponse(
                    savedUser.getId(),
                    savedUser.getEmail(),
                    savedUser.getRole(),
                    savedUser.isEnabled(),
                    savedUser.getCreatedAt()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists"
            );
        }
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
        );
    }
}