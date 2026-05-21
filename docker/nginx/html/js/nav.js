/** 底部导航：根据当前页面高亮对应 Tab */
(function () {
  const page = window.location.pathname.split('/').pop() || 'index.html';
  const mode = new URLSearchParams(window.location.search).get('mode');

  document.querySelectorAll('.bottom-nav .nav-item[data-nav]').forEach(function (el) {
    const nav = el.getAttribute('data-nav');
    let active = false;
    if (nav === 'home'       && page === 'index.html') active = true;
    if (nav === 'ingredient' && page === 'ingredient-list.html') active = true;
    if (nav === 'shop'       && page === 'shop-list.html' && mode !== 'voucher') active = true;
    if (nav === 'voucher'    && page === 'shop-list.html' && mode === 'voucher') active = true;
    if (nav === 'profile'    && page === 'profile.html') active = true;
    if (nav === 'merchant'   && page === 'product-list.html') active = true;
    if (active) el.classList.add('active');
  });
})();
