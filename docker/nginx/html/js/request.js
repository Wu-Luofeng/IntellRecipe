/**
 * 全局 axios 拦截器
 * 所有页面在引入 axios 后引入本文件即可获得：
 *   - 请求拦截：自动注入 authorization header
 *   - 响应拦截：401 时清除 token 并跳转登录页
 */
(function () {
    // 请求拦截：自动注入 token
    axios.interceptors.request.use(function (config) {
        var token = localStorage.getItem('token');
        if (token) {
            config.headers['authorization'] = token;
        }
        return config;
    }, function (error) {
        return Promise.reject(error);
    });

    // 响应拦截：401 时清 token；游客模式或公开页面不跳转
    axios.interceptors.response.use(function (response) {
        return response;
    }, function (error) {
        if (error.response && error.response.status === 401) {
            localStorage.removeItem('token');
            // 游客模式或公开页面不跳转登录
            if (sessionStorage.getItem('guestMode')) return Promise.reject(error);
            var page = window.location.pathname.split('/').pop();
            var publicPages = ['index.html','ingredient-list.html','shop-list.html','product-list.html','product-detail.html'];
            if (page !== 'login.html' && publicPages.indexOf(page) === -1) {
                sessionStorage.setItem('redirectAfterLogin', window.location.href);
                window.location.href = 'login.html';
            }
        }
        return Promise.reject(error);
    });
})();
