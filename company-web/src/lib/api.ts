/**
 * Small centralized API client for the Pathora backend, mirroring admin-web's own
 * src/lib/api.ts pattern (and mobile's lib/api.ts). Every request goes through `request()`
 * below — never scatter raw fetch calls across components. Never log a password, the JWT, or
 * the Authorization header anywhere in this module.
 */

import { clearStoredToken, getStoredToken } from './token'

// VITE_API_BASE_URL is public browser configuration, never a secret (see .env.example and
// README). Empty/unset -> relative /api/v1, so dev traffic flows through the Vite proxy in
// vite.config.ts straight to the backend with no CORS setup needed.
const configuredBase = import.meta.env.VITE_API_BASE_URL?.trim()
const API_BASE_URL = configuredBase ? configuredBase.replace(/\/+$/, '') : '/api/v1'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export class NetworkError extends Error {
  constructor(message = 'Unable to reach the server. Check your connection and try again.') {
    super(message)
    this.name = 'NetworkError'
  }
}

type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/** AuthProvider registers itself here on mount so a 401 from ANY request — not just a
 *  deliberate logout — clears the session and bounces the user back to /login. */
export function registerUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler
}

function friendlyMessageForStatus(status: number): string {
  switch (status) {
    case 400:
      return 'That request was not valid. Please check the details and try again.'
    case 401:
      return 'Your session has expired. Please log in again.'
    case 403:
      return 'You do not have permission to do that.'
    case 404:
      return 'That could not be found.'
    case 409:
      return 'This conflicts with the current state — please refresh and try again.'
    default:
      return status >= 500
        ? 'Something went wrong on our end. Please try again later.'
        : 'Something went wrong. Please try again.'
  }
}

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'

async function request<TResponse>(method: Method, path: string, body?: unknown): Promise<TResponse> {
  const token = getStoredToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new NetworkError()
  }

  if (!response.ok) {
    if (response.status === 401) {
      // A stale/invalid/expired token can never be partially trusted — drop it and let the
      // app fall back to the login screen, regardless of which request happened to fail.
      clearStoredToken()
      unauthorizedHandler?.()
    }
    throw new ApiError(response.status, friendlyMessageForStatus(response.status))
  }

  const text = await response.text()
  if (!text) {
    return undefined as TResponse
  }

  try {
    return JSON.parse(text) as TResponse
  } catch {
    throw new ApiError(response.status, 'Received an unexpected response from the server.')
  }
}

// ===== Auth =====

export type Role = 'INTERN' | 'COMPANY' | 'ADMIN'

export type LoginPayload = {
  email: string
  password: string
}

/** Mirrors the backend's LoginResponse exactly (com.internplatform.auth.LoginResponse). */
export type LoginResponse = {
  accessToken: string
  tokenType: string
  userId: string
  email: string
  role: Role
}

/** Mirrors the backend's CurrentUserResponse (GET /auth/me). */
export type CurrentUserResponse = {
  userId: string
  email: string
  role: Role
}

export function login(payload: LoginPayload): Promise<LoginResponse> {
  return request<LoginResponse>('POST', '/auth/login', payload)
}

export type RegisterPayload = {
  email: string
  password: string
}

/** Mirrors the backend's RegisterResponse exactly (com.internplatform.auth.RegisterResponse).
 *  Registration never auto-logs-in — no token is returned here, matching the mobile app's own
 *  register/company.tsx, which redirects to the login screen afterward rather than signing
 *  the new account in directly. */
export type RegisterResponse = {
  id: string
  email: string
  role: Role
  enabled: boolean
  createdAt: string
}

/** POST /auth/register/company — the exact same endpoint and 2-field payload
 *  (RegisterRequest: email, password) the mobile app's register/company.tsx uses. Every other
 *  company detail (name, industry, etc.) is collected afterward via the existing onboarding
 *  flow (PATCH /company/profile), not at registration. Throws ApiError with status 409 if the
 *  email is already registered — the backend's own duplicate-email check, never re-implemented
 *  here. The new account always starts PENDING approval (CompanyProfile's own default); this
 *  call never grants ATS or marketplace access. */
export function registerCompany(payload: RegisterPayload): Promise<RegisterResponse> {
  return request<RegisterResponse>('POST', '/auth/register/company', payload)
}

// ===== Password recovery (existing OTP-based flow, shared with mobile) =====

export type ForgotPasswordPayload = {
  email: string
}

/** Deliberately the same message regardless of whether the email exists — mirrors the
 *  backend's own GENERIC_MESSAGE in PasswordResetController, which never reveals whether an
 *  account exists for a given email. */
export type ForgotPasswordResponse = {
  message: string
}

export function forgotPassword(payload: ForgotPasswordPayload): Promise<ForgotPasswordResponse> {
  return request<ForgotPasswordResponse>('POST', '/auth/forgot-password', payload)
}

export type VerifyOtpPayload = {
  email: string
  code: string
}

export type VerifyOtpResponse = {
  resetToken: string
  expiresAt: string
}

export function verifyOtp(payload: VerifyOtpPayload): Promise<VerifyOtpResponse> {
  return request<VerifyOtpResponse>('POST', '/auth/verify-otp', payload)
}

export type ResetPasswordPayload = {
  resetToken: string
  newPassword: string
}

export type ResetPasswordResponse = {
  message: string
}

export function resetPassword(payload: ResetPasswordPayload): Promise<ResetPasswordResponse> {
  return request<ResetPasswordResponse>('POST', '/auth/reset-password', payload)
}

export function getCurrentUser(): Promise<CurrentUserResponse> {
  return request<CurrentUserResponse>('GET', '/auth/me')
}

// ===== Company membership (Phase 1) =====

export type AtsMemberRole = 'OWNER' | 'RECRUITER' | 'INTERVIEWER'
export type MemberStatus = 'ACTIVE' | 'INVITED' | 'REVOKED'

/** Mirrors the backend's CompanyMemberResponse (GET /company/ats/members/me). Always reachable
 *  regardless of company approval status — the same "always reachable" convention as
 *  getApprovalStatus below. */
export type CompanyMembership = {
  memberId: string | null
  companyProfileId: string
  role: AtsMemberRole
  status: MemberStatus
  createdAt: string | null
}

export function getOwnMembership(): Promise<CompanyMembership> {
  return request<CompanyMembership>('GET', '/company/ats/members/me')
}

// ===== Company profile =====

export type CompanyProfile = {
  id: string
  userId: string
  companyName: string | null
  industry: string | null
  companySize: string | null
  location: string | null
  website: string | null
  description: string | null
  contactName: string | null
  contactPhone: string | null
  onboardingCompleted: boolean
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export function getCompanyProfile(): Promise<CompanyProfile> {
  return request<CompanyProfile>('GET', '/company/profile')
}

export type CompanyApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export type CompanyApprovalStatusResponse = {
  status: CompanyApprovalStatus
  reviewedAt: string | null
}

export function getCompanyApprovalStatus(): Promise<CompanyApprovalStatusResponse> {
  return request<CompanyApprovalStatusResponse>('GET', '/company/approval-status')
}

// ===== Internships (existing backend endpoints, shared with mobile) =====

export type InternshipWorkMode = 'ONSITE' | 'HYBRID' | 'REMOTE'
export type InternshipStatus = 'OPEN' | 'CLOSED'
export type ApplicationMethod = 'INTERNAL' | 'EXTERNAL'

export type InternshipSkill = {
  id: string
  name: string
}

/** Mirrors the backend's InternshipResponse field-for-field. */
export type CompanyInternship = {
  id: string
  companyName: string
  title: string
  description: string
  location: string
  workMode: InternshipWorkMode
  applicationDeadline: string
  startDate: string | null
  durationMonths: number | null
  status: InternshipStatus
  skills: InternshipSkill[]
  targetMajors: string[]
  minimumAcademicYear: number | null
  applicationMethod: ApplicationMethod
  externalApplicationUrl: string | null
  createdAt: string
  updatedAt: string
}

/** Newest first — never re-sort client-side, matching the backend's own ordering contract. */
export function getCompanyInternships(): Promise<CompanyInternship[]> {
  return request<CompanyInternship[]>('GET', '/company/internships')
}

/** Single internship, scoped to the authenticated company — the existing
 *  GET /company/internships/{id} endpoint, reused as-is (see InternshipController). 404s if
 *  the id doesn't belong to this company. */
export function getCompanyInternship(internshipId: string): Promise<CompanyInternship> {
  return request<CompanyInternship>('GET', `/company/internships/${internshipId}`)
}

/** The shared skill catalog — mirrors the backend's SkillResponse. Lives under /intern/skills
 *  but is explicitly carved out for COMPANY access too in SecurityConfig (needed here for
 *  selecting an internship's required skills), exactly as the mobile app already does. */
export function getSkillCatalog(): Promise<InternshipSkill[]> {
  return request<InternshipSkill[]>('GET', '/intern/skills')
}

export type CompanyInternshipCreatePayload = {
  title: string
  description: string
  location: string
  workMode: InternshipWorkMode
  applicationDeadline: string
  startDate?: string
  durationMonths?: number
  skillIds: string[]
  targetMajors?: string[]
  minimumAcademicYear?: number | null
  applicationMethod: ApplicationMethod
  /** Required (and only meaningful) when applicationMethod is EXTERNAL; omit for INTERNAL. */
  externalApplicationUrl?: string
}

export function createCompanyInternship(payload: CompanyInternshipCreatePayload): Promise<CompanyInternship> {
  return request<CompanyInternship>('POST', '/company/internships', payload)
}

/**
 * Partial update payload for PATCH /company/internships/{id}, mirroring the backend's PATCH
 * semantics exactly (see InternshipUpdateRequest): an omitted field is left unchanged.
 * minimumAcademicYear must always be sent explicitly (never omitted) — selecting "no
 * restriction" requires an explicit JSON null to clear a previously-set value; an omitted key
 * would leave the old restriction in place. externalApplicationUrl needs no such explicit
 * clearing: the backend auto-clears it whenever the final applicationMethod resolves to
 * INTERNAL, so switching to INTERNAL only needs applicationMethod: 'INTERNAL' with
 * externalApplicationUrl omitted. startDate/durationMonths have no clear-to-null path on the
 * backend at all (no tri-state there) — omitting them always leaves the existing value as-is.
 */
export type CompanyInternshipUpdatePayload = Partial<{
  title: string
  description: string
  location: string
  workMode: InternshipWorkMode
  applicationDeadline: string
  startDate: string
  durationMonths: number
  skillIds: string[]
  targetMajors: string[]
  applicationMethod: ApplicationMethod
  externalApplicationUrl: string
}> & {
  minimumAcademicYear: number | null
}

export function updateCompanyInternship(
  internshipId: string,
  payload: CompanyInternshipUpdatePayload,
): Promise<CompanyInternship> {
  return request<CompanyInternship>('PATCH', `/company/internships/${internshipId}`, payload)
}

/** Closing is one-way (OPEN -> CLOSED only) — the backend exposes no "reopen". */
export function closeCompanyInternship(internshipId: string): Promise<CompanyInternship> {
  return request<CompanyInternship>('POST', `/company/internships/${internshipId}/close`)
}

// ===== Custom application questions (INTERNAL internships only) =====

export type ApplicationQuestionType = 'SHORT_TEXT' | 'LONG_TEXT'

/** Mirrors the backend's InternshipApplicationQuestionResponse. */
export type ApplicationQuestion = {
  id: string
  internshipId: string
  questionText: string
  questionType: ApplicationQuestionType
  required: boolean
  displayOrder: number
  createdAt: string
  updatedAt: string
}

export type ApplicationQuestionCreatePayload = {
  questionText: string
  questionType: ApplicationQuestionType
  required: boolean
  displayOrder: number
}

/** Partial update — an omitted field is left unchanged. */
export type ApplicationQuestionUpdatePayload = Partial<ApplicationQuestionCreatePayload>

/** Questions are returned in the backend's own canonical order — never re-sort client-side. */
export function getCompanyApplicationQuestions(internshipId: string): Promise<ApplicationQuestion[]> {
  return request<ApplicationQuestion[]>('GET', `/company/internships/${internshipId}/application-questions`)
}

/** Throws ApiError with status 409 once this internship has received its first application —
 *  questions are frozen at that point. Callers should check `error.status === 409` to show a
 *  specific "locked" message. */
export function createCompanyApplicationQuestion(
  internshipId: string,
  payload: ApplicationQuestionCreatePayload,
): Promise<ApplicationQuestion> {
  return request<ApplicationQuestion>(
    'POST',
    `/company/internships/${internshipId}/application-questions`,
    payload,
  )
}

/** Same 409-when-locked behavior as createCompanyApplicationQuestion. */
export function updateCompanyApplicationQuestion(
  internshipId: string,
  questionId: string,
  payload: ApplicationQuestionUpdatePayload,
): Promise<ApplicationQuestion> {
  return request<ApplicationQuestion>(
    'PATCH',
    `/company/internships/${internshipId}/application-questions/${questionId}`,
    payload,
  )
}

/** Same 409-when-locked behavior as createCompanyApplicationQuestion. */
export function deleteCompanyApplicationQuestion(internshipId: string, questionId: string): Promise<void> {
  return request<void>('DELETE', `/company/internships/${internshipId}/application-questions/${questionId}`)
}

// ===== ATS pipeline templates =====

export type TemplateStage = {
  id: string
  name: string
  position: number
}

/** Mirrors the backend's PipelineTemplateResponse. */
export type PipelineTemplate = {
  id: string
  companyProfileId: string
  name: string
  description: string | null
  isDefault: boolean
  archived: boolean
  stages: TemplateStage[]
  version: number
  createdAt: string
  updatedAt: string
}

export type PipelineTemplateCreatePayload = {
  name: string
  description?: string
}

/** Partial update — an omitted field is left unchanged. Setting isDefault true clears any
 *  other template this company currently has marked default. */
export type PipelineTemplateUpdatePayload = Partial<{
  name: string
  description: string
  isDefault: boolean
}>

export function getPipelineTemplates(includeArchived = false): Promise<PipelineTemplate[]> {
  const query = includeArchived ? '?includeArchived=true' : ''
  return request<PipelineTemplate[]>('GET', `/company/ats/pipeline-templates${query}`)
}

export function getPipelineTemplate(templateId: string): Promise<PipelineTemplate> {
  return request<PipelineTemplate>('GET', `/company/ats/pipeline-templates/${templateId}`)
}

export function createPipelineTemplate(payload: PipelineTemplateCreatePayload): Promise<PipelineTemplate> {
  return request<PipelineTemplate>('POST', '/company/ats/pipeline-templates', payload)
}

export function updatePipelineTemplate(
  templateId: string,
  payload: PipelineTemplateUpdatePayload,
): Promise<PipelineTemplate> {
  return request<PipelineTemplate>('PATCH', `/company/ats/pipeline-templates/${templateId}`, payload)
}

export function duplicatePipelineTemplate(templateId: string): Promise<PipelineTemplate> {
  return request<PipelineTemplate>('POST', `/company/ats/pipeline-templates/${templateId}/duplicate`)
}

export function archivePipelineTemplate(templateId: string): Promise<PipelineTemplate> {
  return request<PipelineTemplate>('POST', `/company/ats/pipeline-templates/${templateId}/archive`)
}

export function addTemplateStage(templateId: string, name: string): Promise<TemplateStage> {
  return request<TemplateStage>('POST', `/company/ats/pipeline-templates/${templateId}/stages`, { name })
}

export function renameTemplateStage(templateId: string, stageId: string, name: string): Promise<TemplateStage> {
  return request<TemplateStage>(
    'PATCH',
    `/company/ats/pipeline-templates/${templateId}/stages/${stageId}`,
    { name },
  )
}

/** Throws ApiError with status 409 if this would remove the template's last remaining stage —
 *  callers should check `error.status === 409` to show a specific message. */
export function deleteTemplateStage(templateId: string, stageId: string): Promise<void> {
  return request<void>('DELETE', `/company/ats/pipeline-templates/${templateId}/stages/${stageId}`)
}

/** stageIds must be the complete, reordered list of the template's current stage ids. */
export function reorderTemplateStages(templateId: string, stageIds: string[]): Promise<TemplateStage[]> {
  return request<TemplateStage[]>('PUT', `/company/ats/pipeline-templates/${templateId}/stages/order`, { stageIds })
}

// ===== ATS internship pipelines =====
//
// All of these call the existing InternshipPipelineController endpoints exactly as they
// already exist on the backend — nothing here is new backend functionality, only a client for
// what Task B already shipped (see backend/.../ats/pipeline/InternshipPipelineController.java).

export type PipelineStage = {
  id: string
  name: string
  position: number
}

/** Mirrors the backend's InternshipPipelineResponse. sourceTemplateId/sourceTemplateName are
 *  both null only for a pipeline assigned from the hardcoded starter rather than a real
 *  company template. */
export type InternshipPipeline = {
  id: string
  internshipId: string
  companyProfileId: string
  sourceTemplateId: string | null
  sourceTemplateName: string | null
  stages: PipelineStage[]
  version: number
  createdAt: string
  updatedAt: string
}

/** Throws ApiError with status 404 when no pipeline has been assigned to this internship yet —
 *  the same "not created yet" convention as getCompanyProfile before onboarding; callers
 *  should treat a 404 here as a normal, expected state, not an error. */
export function getInternshipPipeline(internshipId: string): Promise<InternshipPipeline> {
  return request<InternshipPipeline>('GET', `/company/ats/internships/${internshipId}/pipeline`)
}

/** templateId omitted -> the company's own default template if one is marked, else the
 *  hardcoded starter (Applied -> Screening -> Interview -> Offer). Throws ApiError with status
 *  409 if this internship already has a pipeline assigned. */
export function assignInternshipPipeline(internshipId: string, templateId?: string): Promise<InternshipPipeline> {
  return request<InternshipPipeline>(
    'POST',
    `/company/ats/internships/${internshipId}/pipeline`,
    { templateId: templateId ?? null },
  )
}

export function addPipelineStage(internshipId: string, name: string): Promise<PipelineStage> {
  return request<PipelineStage>('POST', `/company/ats/internships/${internshipId}/pipeline/stages`, { name })
}

export function renamePipelineStage(internshipId: string, stageId: string, name: string): Promise<PipelineStage> {
  return request<PipelineStage>(
    'PATCH',
    `/company/ats/internships/${internshipId}/pipeline/stages/${stageId}`,
    { name },
  )
}

/** Throws ApiError with status 409 if this would remove the pipeline's last remaining stage. */
export function deletePipelineStage(internshipId: string, stageId: string): Promise<void> {
  return request<void>('DELETE', `/company/ats/internships/${internshipId}/pipeline/stages/${stageId}`)
}

/** stageIds must be the complete, reordered list of the pipeline's current stage ids. */
export function reorderPipelineStages(internshipId: string, stageIds: string[]): Promise<PipelineStage[]> {
  return request<PipelineStage[]>(
    'PUT',
    `/company/ats/internships/${internshipId}/pipeline/stages/order`,
    { stageIds },
  )
}
