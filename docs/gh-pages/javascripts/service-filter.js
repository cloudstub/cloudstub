// Drives the Services landing page: a type-to-filter box over the service cards,
// and whole-card click-through to each service's page.
(function () {
  function cards() {
    return Array.prototype.slice.call(
      document.querySelectorAll(".grid.cards > ul > li"),
    );
  }

  function init() {
    var items = cards();

    items.forEach(function (card) {
      var link = card.querySelector("a[href]");
      if (!link) return;
      card.style.cursor = "pointer";
      card.addEventListener("click", function (event) {
        if (event.target.closest("a")) return; // real link clicks navigate normally
        window.location.href = link.href;
      });
    });

    var input = document.getElementById("service-filter");
    if (!input) return;
    input.addEventListener("input", function () {
      var query = input.value.trim().toLowerCase();
      items.forEach(function (card) {
        var match = card.textContent.toLowerCase().indexOf(query) >= 0;
        card.style.display = match ? "" : "none";
      });
    });
  }

  document.addEventListener("DOMContentLoaded", init);
})();
