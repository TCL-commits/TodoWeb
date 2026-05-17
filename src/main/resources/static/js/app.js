(function () {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || "";
  const csrfHeader =
    document.querySelector('meta[name="_csrf_header"]')?.content ||
    "X-CSRF-TOKEN";

  const shouldProtect = (method) => {
    const normalized = (method || "GET").toUpperCase();
    return !["GET", "HEAD", "OPTIONS", "TRACE"].includes(normalized);
  };

  const attachCsrfToken = (headers) => {
    if (!csrfToken) {
      return headers;
    }

    const nextHeaders =
      headers instanceof Headers
        ? new Headers(headers)
        : new Headers(headers || {});
    if (!nextHeaders.has(csrfHeader)) {
      nextHeaders.set(csrfHeader, csrfToken);
    }
    return nextHeaders;
  };

  const originalFetch = window.fetch.bind(window);
  window.fetch = (input, init = {}) => {
    if (csrfToken && shouldProtect(init.method)) {
      init.headers = attachCsrfToken(init.headers);
    }
    return originalFetch(input, init);
  };

  const injectTokenIntoForms = () => {
    document
      .querySelectorAll(
        'form[method="post"], form[method="put"], form[method="patch"], form[method="delete"]',
      )
      .forEach((form) => {
        const existing = form.querySelector(`input[name="${csrfHeader}"]`);
        if (existing || !csrfToken) {
          return;
        }

        const tokenField = document.createElement("input");
        tokenField.type = "hidden";
        tokenField.name = csrfHeader;
        tokenField.value = csrfToken;
        form.appendChild(tokenField);
      });
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", injectTokenIntoForms, {
      once: true,
    });
  } else {
    injectTokenIntoForms();
  }
})();
