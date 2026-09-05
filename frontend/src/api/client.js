import axios from 'axios';

const ACCESS_TOKEN_KEY = 'lifelink.accessToken';
const REFRESH_TOKEN_KEY = 'lifelink.refreshToken';

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_TOKEN_KEY),
  refresh: () => localStorage.getItem(REFRESH_TOKEN_KEY),
  save({ accessToken, refreshToken }) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

export const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((config) => {
  const token = tokenStore.access();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Called when refreshing fails, so the app can drop back to the login page. */
let onSessionExpired = () => {};
export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler;
}

// Access tokens last 15 minutes, so a 401 mid-session is expected. One refresh
// runs at a time and every request that arrived during it waits on the same
// promise, otherwise parallel 401s would each rotate the refresh token and all
// but one would be left holding a revoked one.
let refreshInFlight = null;

function refreshAccessToken() {
  if (!refreshInFlight) {
    const refreshToken = tokenStore.refresh();
    if (!refreshToken) {
      return Promise.reject(new Error('No refresh token'));
    }
    refreshInFlight = axios
      .post('/api/auth/refresh', { refreshToken })
      .then((response) => {
        tokenStore.save(response.data);
        return response.data.accessToken;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const request = error.config;
    const isAuthCall = request?.url?.startsWith('/auth/');

    if (error.response?.status !== 401 || request?._retried || isAuthCall) {
      return Promise.reject(error);
    }

    request._retried = true;
    try {
      const accessToken = await refreshAccessToken();
      request.headers.Authorization = `Bearer ${accessToken}`;
      return api(request);
    } catch (refreshError) {
      tokenStore.clear();
      onSessionExpired();
      return Promise.reject(refreshError);
    }
  },
);

/** Pulls the readable message out of the API's ProblemDetail responses. */
export function errorMessage(error, fallback = 'Something went wrong') {
  const problem = error?.response?.data;
  if (!problem) return fallback;
  if (problem.errors) {
    const fields = Object.entries(problem.errors);
    if (fields.length) return fields.map(([field, message]) => `${field}: ${message}`).join(', ');
  }
  return problem.detail || problem.title || fallback;
}
