package com.internplatform.internprofile;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "skills")
public class Skill {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** True for PATH's curated catalog (the original seed plus any admin-curated expansion);
     *  false for a skill an intern or company created themselves via "Add custom skill" when
     *  nothing in the catalog matched (see InternProfileService#resolveOrCreateSkill). Purely
     *  informational today — nothing filters or behaves differently based on this flag, so a
     *  custom skill is just as usable (selectable, CV-detectable, rubric-eligible) as a
     *  standard one. Defaults to true so every pre-V30 row needs no backfill. */
    @Column(name = "is_standard", nullable = false)
    private boolean standard = true;

    /**
     * Alternate names that resolve to this same canonical skill (e.g. "JS" for JavaScript,
     * "Postgres" for PostgreSQL) — see V23/V24. Seeded via migration only today; there is no
     * admin-facing way to add aliases yet, so this is read-only from application code.
     */
    @ElementCollection
    @CollectionTable(
            name = "skill_aliases",
            joinColumns = @JoinColumn(name = "skill_id")
    )
    @Column(name = "alias", length = 100)
    private Set<String> aliases = new HashSet<>();

    protected Skill() {
    }

    public Skill(String name) {
        this.name = name;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isStandard() {
        return standard;
    }

    public void setStandard(boolean standard) {
        this.standard = standard;
    }

    public Set<String> getAliases() {
        return aliases;
    }
}
