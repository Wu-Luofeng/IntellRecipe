/**
 * IntellRecipe 全局工具库
 * 所有页面在 axios.min.js + request.js 之后引入
 */
const Utils = {
  /* ── Token ── */
  getToken()       { return localStorage.getItem('token'); },
  setToken(t)      { localStorage.setItem('token', t); },
  removeToken()    { localStorage.removeItem('token'); },

  /* ── 用户信息缓存 ── */
  getUserInfo() {
    const s = localStorage.getItem('userInfo');
    try { return s ? JSON.parse(s) : null; } catch { return null; }
  },
  setUserInfo(info) { localStorage.setItem('userInfo', JSON.stringify(info)); },
  removeUserInfo()  { localStorage.removeItem('userInfo'); },

  /* ── 登录态检查（未登录则跳转） ── */
  requireLogin() {
    if (!this.getToken()) {
      sessionStorage.setItem('redirectAfterLogin', window.location.href);
      window.location.href = 'login.html';
      return false;
    }
    return true;
  },

  /* ── 退出登录 ── */
  logout() {
    this.removeToken();
    this.removeUserInfo();
    window.location.href = 'login.html';
  },

  /* ── Toast 提示 ── */
  toast(msg, type = 'info') {
    let el = document.getElementById('__toast__');
    if (!el) {
      el = document.createElement('div');
      el.id = '__toast__';
      el.className = 'toast';
      document.body.appendChild(el);
    }
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(el.__timer);
    el.__timer = setTimeout(() => el.classList.remove('show'), 2500);
  },

  /* ── 手机号脱敏 ── */
  maskPhone(phone) {
    if (!phone || phone.length < 7) return phone;
    return phone.slice(0, 3) + '****' + phone.slice(-4);
  },

  /* ── 格式化时间 ── */
  formatDate(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  },

  /* ── 从 URL 拿参数 ── */
  getParam(key) {
    return new URLSearchParams(window.location.search).get(key);
  },

  /* ── 异步获取当前用户信息（优先缓存，失败时静默） ── */
  async fetchUserInfo(force = false) {
    if (!this.getToken()) return null;
    if (!force) {
      const cached = this.getUserInfo();
      if (cached) return cached;
    }
    try {
      const res = await axios.get('/api/user/me');
      if (res.data.success) {
        this.setUserInfo(res.data.data);
        return res.data.data;
      }
    } catch (_) {}
    return null;
  },

  /* ── 默认头像（当 icon 为空时用） ── */
  defaultAvatar: 'https://api.dicebear.com/7.x/thumbs/svg?seed=IntellRecipe',
  avatar(icon) { return icon && icon.trim() ? icon : this.defaultAvatar; },
};
