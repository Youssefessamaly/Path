/* PATH support form: no authentication, no tokens, nothing persisted in browser storage. */
(() => {
  'use strict';

  const API_BASE = 'https://path-backend.hostless.app/api/v1';
  const byId = (id) => document.getElementById(id);

  const form = byId('contactForm');
  const submitButton = byId('submitButton');
  const formSuccess = byId('formSuccess');
  const formError = byId('formError');
  const messageInput = byId('message');
  const messageCount = byId('messageCount');

  messageInput.addEventListener('input', () => {
    messageCount.textContent = String(messageInput.value.length);
  });

  function showNotice(el, message) {
    el.hidden = !message;
    if (message) el.textContent = message;
  }

  function showStaticSuccess() {
    // formSuccess already contains its full message in the HTML — only toggle visibility,
    // never overwrite it (unlike showNotice's dynamic-error-text path below).
    formSuccess.hidden = false;
  }

  function setSubmitting(isSubmitting) {
    submitButton.disabled = isSubmitting;
    submitButton.textContent = isSubmitting ? 'Sending…' : 'Send message';
  }

  async function postSupportRequest(payload, timeout = 15000) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      const response = await fetch(`${API_BASE}/support/contact`, {
        method: 'POST',
        mode: 'cors',
        credentials: 'omit',
        redirect: 'error',
        referrerPolicy: 'no-referrer',
        cache: 'no-store',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify(payload),
        signal: controller.signal,
      });
      if (!response.ok) {
        const err = new Error('The server could not complete the request.');
        err.status = response.status;
        throw err;
      }
      return await response.json();
    } finally {
      clearTimeout(timer);
    }
  }

  function describeError(error) {
    if (error.name === 'AbortError') return 'The request timed out. Please check your connection and try again.';
    if (error.status === 400) return 'Please check your information and try again.';
    if (error.status === 429) return 'Too many messages sent recently. Please try again later, or email us directly.';
    if (error.status >= 500) return 'PATH is temporarily unavailable. Please try again later, or email us directly.';
    return 'Something went wrong. Please try again, or email us directly at path.app.support@gmail.com.';
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    showNotice(formError, '');
    showNotice(formSuccess, '');

    if (!form.reportValidity()) return;

    const payload = {
      fullName: byId('fullName').value.trim(),
      email: byId('email').value.trim(),
      accountType: byId('accountType').value,
      subject: byId('subject').value.trim(),
      message: messageInput.value.trim(),
      website: byId('website').value, // honeypot — always blank from a real visitor
    };

    setSubmitting(true);
    try {
      await postSupportRequest(payload);
      form.reset();
      messageCount.textContent = '0';
      showStaticSuccess();
      formSuccess.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } catch (error) {
      showNotice(formError, describeError(error));
    } finally {
      setSubmitting(false);
    }
  });
})();
