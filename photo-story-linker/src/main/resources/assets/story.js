(function () {
  const endpoint = "/apis/api.photo-story-linker.xiongfan.me/v1alpha1/bindings";

  if (!location.pathname.startsWith("/photos")) {
    return;
  }

  const escapeHtml = (value) =>
    String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");

  const normalize = (value) => String(value || "").trim();

  const keyForFigure = (figure) => {
    const detailLink = figure.querySelector("a.photo-link[href]");
    const href = detailLink ? normalize(detailLink.getAttribute("href")) : "";
    const match = href.match(/\/photos\/([^/?#]+)/);
    if (match) {
      return decodeURIComponent(match[1]);
    }
    const img = figure.querySelector("img");
    return normalize(img && img.getAttribute("alt"));
  };

  const enhance = (bindings) => {
    const byPhotoName = new Map(bindings.map((item) => [normalize(item.photoName), item]));
    document.querySelectorAll(".photo-masonry .photo-item").forEach((figure) => {
      const binding = byPhotoName.get(keyForFigure(figure));
      if (!binding || figure.dataset.photoStoryEnhanced === "true") {
        return;
      }
      figure.dataset.photoStoryEnhanced = "true";
      figure.classList.add("photo-story-linked");

      const openInNewTab = binding.openMode === "NEW_TAB";
      const target = openInNewTab ? ' target="_blank" rel="noopener noreferrer"' : "";
      const overlay = document.createElement("div");
      overlay.className = "photo-story-overlay";
      overlay.innerHTML = `
        <div class="photo-story-panel">
          <h3 class="photo-story-title">${escapeHtml(binding.title)}</h3>
          ${binding.teaser ? `<p class="photo-story-teaser">${escapeHtml(binding.teaser)}</p>` : ""}
          <a class="photo-story-button" href="${escapeHtml(binding.postUrl)}"${target}>${escapeHtml(binding.badgeText || "Read story")}</a>
        </div>
      `;
      figure.appendChild(overlay);

      figure.addEventListener("click", (event) => {
        const isTouch = matchMedia("(hover: none)").matches;
        if (!isTouch || figure.classList.contains("photo-story-active")) {
          return;
        }
        event.preventDefault();
        document.querySelectorAll(".photo-story-active").forEach((item) => {
          if (item !== figure) {
            item.classList.remove("photo-story-active");
          }
        });
        figure.classList.add("photo-story-active");
      });
    });
  };

  fetch(endpoint, { credentials: "same-origin" })
    .then((response) => (response.ok ? response.json() : []))
    .then((bindings) => enhance(Array.isArray(bindings) ? bindings : []))
    .catch(() => {});
})();
