import { Navigate, Route, Routes } from 'react-router-dom'
import { CompanyShell } from './components/CompanyShell'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { VerifyOtpPage } from './pages/VerifyOtpPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { DashboardPage } from './pages/DashboardPage'
import { InternshipsListPage } from './pages/internships/InternshipsListPage'
import { InternshipDetailPage } from './pages/internships/InternshipDetailPage'
import { InternshipFormPage } from './pages/internships/InternshipFormPage'
import { ApplicationQuestionsPage } from './pages/internships/ApplicationQuestionsPage'
import { PipelineTemplatesListPage } from './pages/pipeline-templates/PipelineTemplatesListPage'
import { PipelineTemplateEditorPage } from './pages/pipeline-templates/PipelineTemplateEditorPage'

/**
 * Route map:
 *   /login                              public
 *   /register                           public — create a new COMPANY account
 *   /forgot-password                    public — step 1 of the existing OTP reset flow
 *   /verify-otp                         public — step 2 (requires router state from step 1)
 *   /reset-password                     public — step 3 (requires router state from step 2)
 *   /dashboard                          protected
 *   /internships                        protected
 *   /internships/new                    protected
 *   /internships/:internshipId          protected (hosts the Recruitment Pipeline section)
 *   /internships/:internshipId/edit     protected
 *   /internships/:internshipId/application-questions   protected
 *   /pipeline-templates                 protected
 *   /pipeline-templates/:templateId     protected
 * Anything else redirects to /dashboard, which itself redirects an unauthenticated visitor to
 * /login (see ProtectedRoute). Registration only ever creates the account — it never signs the
 * new company in, matching the mobile app's own register/company.tsx exactly.
 */
function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/verify-otp" element={<VerifyOtpPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />

        <Route
          element={
            <ProtectedRoute>
              <CompanyShell />
            </ProtectedRoute>
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/internships" element={<InternshipsListPage />} />
          <Route path="/internships/new" element={<InternshipFormPage mode="create" />} />
          <Route path="/internships/:internshipId" element={<InternshipDetailPage />} />
          <Route path="/internships/:internshipId/edit" element={<InternshipFormPage mode="edit" />} />
          <Route
            path="/internships/:internshipId/application-questions"
            element={<ApplicationQuestionsPage />}
          />
          <Route path="/pipeline-templates" element={<PipelineTemplatesListPage />} />
          <Route path="/pipeline-templates/:templateId" element={<PipelineTemplateEditorPage />} />
        </Route>

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  )
}

export default App
