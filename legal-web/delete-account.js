/* PATH self-service deletion: no tokens or passwords are persisted in browser storage. */
(() => {
  'use strict';

  const API_BASE = 'https://path-backend.hostless.app/api/v1';
  const byId = (id) => document.getElementById(id);
  const signInStep = byId('signInStep');
  const confirmStep = byId('confirmStep');
  const successStep = byId('successStep');
  const loginForm = byId('loginForm');
  const deleteForm = byId('deleteForm');
  const loginButton = byId('loginButton');
  const deleteButton = byId('deleteButton');
  const confirmText = byId('confirmText');
  const loginPassword = byId('loginPassword');
  const deletePassword = byId('deletePassword');
  let token = null;
  let busy = false;

  function clearCredentials() {
    token = null;
    loginPassword.value = '';
    deletePassword.value = '';
    confirmText.value = '';
  }

  function showError(id, message) {
    const target = byId(id);
    target.textContent = message;
    target.hidden = !message;
  }

  function showStep(step, focusHeading = true) {
    signInStep.hidden = step !== 'login';
    confirmStep.hidden = step !== 'confirm';
    successStep.hidden = step !== 'success';
    byId('progressSignIn').classList.toggle('active', step === 'login');
    byId('progressConfirm').classList.toggle('active', step === 'confirm');
    if (focusHeading && step === 'login') byId('signInTitle').focus();
    if (focusHeading && step === 'confirm') byId('confirmTitle').focus();
    if (focusHeading && step === 'success') byId('successTitle').focus();
  }

  function setBusy(action, value) {
    busy = value;
    loginButton.disabled = value;
    byId('signOutButton').disabled = value;
    byId('cancelButton').disabled = value;
    loginButton.textContent = action === 'login' && value ? 'Signing in…' : 'Continue securely →';
    deleteButton.textContent = action === 'delete' && value ? 'Deleting account…' : 'Delete my account permanently';
    updateDeleteButton();
  }

  function updateDeleteButton() {
    deleteButton.disabled = busy || !token || confirmText.value.trim() !== 'DELETE' || deletePassword.value.length === 0;
  }

  function resetToLogin(errorMessage = '') {
    clearCredentials();
    byId('signedInEmail').textContent = '';
    showError('deleteError', '');
    showError('loginError', errorMessage);
    loginForm.reset();
    deleteForm.reset();
    showStep('login');
    updateDeleteButton();
  }

  async function api(path, method, body, authToken, timeout = 15000) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      const response = await fetch(`${API_BASE}${path}`, {
        method,
        mode: 'cors',
        credentials: 'omit',
        redirect: 'error',
        referrerPolicy: 'no-referrer',
        cache: 'no-store',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {}),
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        signal: controller.signal,
      });
      if (!response.ok) {
        const err = new Error('The server could not complete the request.');
        err.status = response.status;
        throw err;
      }
      if (response.status === 204) return null;
      return await response.json();
    } finally {
      clearTimeout(timer);
    }
  }

  function describeLoginError(error) {
    if (error.status === 400 || error.status === 401) return 'Invalid email or password. Please try again.';
    if (error.status === 403) return 'This account is disabled. Please contact PATH Support.';
    if (error.status === 429) return 'Too many attempts. Please try again later.';
    if (error.status >= 500) return 'PATH is temporarily unavailable. Please try again later.';
    return 'Unable to sign in. Check your connection and try again.';
  }

  loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy || !loginForm.reportValidity()) return;
    const email = byId('email').value.trim();
    const password = loginPassword.value;
    showError('loginError', '');
    setBusy('login', true);
    loginPassword.value = '';
    try {
      const response = await api('/auth/login', 'POST', { email, password });
      if (!response || typeof response.accessToken !== 'string' || !response.accessToken) {
        throw new Error('Unexpected login response');
      }
      if (response.role === 'ADMIN') {
        throw new Error('Administrators cannot delete accounts through this page. Contact PATH Support.');
      }
      if (response.role !== 'COMPANY' && response.role !== 'INTERN') {
        throw new Error('This account type is not eligible for self-service deletion.');
      }
      // Validate the token with the backend before displaying an identity for deletion.
      const currentUser = await api('/auth/me', 'GET', undefined, response.accessToken);
      if (!currentUser || currentUser.email?.toLowerCase() !== response.email?.toLowerCase() ||
          currentUser.role !== response.role || currentUser.userId !== response.userId) {
        throw new Error('Could not verify your PATH account. Please try again.');
      }
      token = response.accessToken; // memory only: never localStorage/sessionStorage/cookie
      byId('signedInEmail').textContent = currentUser.email;
      deleteForm.reset();
      showError('deleteError', '');
      showStep('confirm');
    } catch (error) {
      token = null;
      showError('loginError', error.status ? describeLoginError(error) :
        (error.message?.includes('Administrators') || error.message?.includes('not eligible') ||
         error.message?.includes('Could not verify')) ? error.message : describeLoginError(error));
    } finally {
      setBusy('login', false);
    }
  });

  confirmText.addEventListener('input', updateDeleteButton);
  deletePassword.addEventListener('input', updateDeleteButton);

  function signOut() {
    if (!busy) resetToLogin();
  }
  byId('signOutButton').addEventListener('click', signOut);
  byId('cancelButton').addEventListener('click', signOut);

  deleteForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy || !token || confirmText.value.trim() !== 'DELETE' || !deletePassword.value) return;
    showError('deleteError', '');
    setBusy('delete', true);
    const currentPassword = deletePassword.value;
    deletePassword.value = '';
    try {
      await api('/auth/me', 'DELETE', { password: currentPassword }, token, 30000);
      clearCredentials();
      byId('signedInEmail').textContent = '';
      loginForm.reset();
      deleteForm.reset();
      showStep('success');
    } catch (error) {
      if (error.status === 400) {
        showError('deleteError', 'Incorrect current password. Please re-enter your password.');
      } else if (error.status === 401) {
        resetToLogin('Your session expired. Please sign in again to continue.');
      } else if (error.status === 403) {
        showError('deleteError', 'This account cannot be deleted through self-service. Please contact PATH Support.');
      } else if (error.status === 429) {
        showError('deleteError', 'Too many attempts. Please try again later.');
      } else if (error.status >= 500) {
        showError('deleteError', 'The server could not confirm deletion. Please contact PATH Support if this continues.');
      } else {
        resetToLogin('We could not confirm whether deletion finished. Try signing in again to check your account status, or contact PATH Support.');
      }
    } finally {
      setBusy('delete', false);
    }
  });

  // Never display a retained account session when the browser restores a page from its back/forward cache.
  window.addEventListener('pageshow', (event) => {
    if (event.persisted) resetToLogin();
  });
  showStep('login', false);
})();
