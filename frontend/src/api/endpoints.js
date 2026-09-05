import { api } from './client';

const unwrap = (response) => response.data;

export const auth = {
  login: (body) => api.post('/auth/login', body).then(unwrap),
  registerDonor: (body) => api.post('/auth/register/donor', body).then(unwrap),
  registerHospital: (body) => api.post('/auth/register/hospital', body).then(unwrap),
  registerBloodBank: (body) => api.post('/auth/register/bloodbank', body).then(unwrap),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }).then(unwrap),
  me: () => api.get('/auth/me').then(unwrap),
  google: (credential) => api.post('/auth/google', { credential }).then(unwrap),
  forgotPassword: (email) => api.post('/auth/password/forgot', { email }).then(unwrap),
  resetPassword: (token, newPassword) =>
    api.post('/auth/password/reset', { token, newPassword }).then(unwrap),
};

export const meta = {
  bloodGroups: () => api.get('/meta/blood-groups').then(unwrap),
  authConfig: () => api.get('/meta/auth-config').then(unwrap),
};

export const donors = {
  profile: () => api.get('/donors/me').then(unwrap),
  updateProfile: (body) => api.put('/donors/me', body).then(unwrap),
  setAvailability: (available) => api.patch('/donors/me/availability', { available }).then(unwrap),
  eligibility: () => api.get('/donors/me/eligibility').then(unwrap),
  matches: () => api.get('/donors/me/matches').then(unwrap),
  donations: () => api.get('/donors/me/donations').then(unwrap),
  opportunities: () => api.get('/donors/me/opportunities').then(unwrap),
  volunteer: (requestId) =>
    api.post(`/donors/me/opportunities/${requestId}/accept`).then(unwrap),
  acceptMatch: (matchId) => api.post(`/matches/${matchId}/accept`).then(unwrap),
  declineMatch: (matchId) => api.post(`/matches/${matchId}/decline`).then(unwrap),
};

export const requests = {
  create: (body) => api.post('/requests', body).then(unwrap),
  list: (params) => api.get('/requests', { params }).then(unwrap),
  detail: (id) => api.get(`/requests/${id}`).then(unwrap),
  matches: (id) => api.get(`/requests/${id}/matches`).then(unwrap),
  history: (id) => api.get(`/requests/${id}/history`).then(unwrap),
  confirm: (id, matchId) => api.post(`/requests/${id}/matches/${matchId}/confirm`).then(unwrap),
  fulfill: (id, body) => api.post(`/requests/${id}/fulfill`, body).then(unwrap),
  cancel: (id, reason) => api.post(`/requests/${id}/cancel`, { reason }).then(unwrap),
};

export const bloodBanks = {
  inventory: () => api.get('/bloodbanks/me/inventory').then(unwrap),
  updateInventory: (items) => api.put('/bloodbanks/me/inventory', { items }).then(unwrap),
  escalations: () => api.get('/bloodbanks/me/escalations').then(unwrap),
  acceptEscalation: (requestId) => api.post(`/escalations/${requestId}/accept`).then(unwrap),
  fulfillEscalation: (requestId, units) =>
    api.post(`/escalations/${requestId}/fulfill`, { units }).then(unwrap),
};

export const admin = {
  verifications: (type) => api.get('/admin/verifications', { params: { type } }).then(unwrap),
  approve: (userId) => api.post(`/admin/verifications/${userId}/approve`).then(unwrap),
  reject: (userId, reason) => api.post(`/admin/verifications/${userId}/reject`, { reason }).then(unwrap),
  stats: () => api.get('/admin/stats').then(unwrap),
  setUserStatus: (userId, status) => api.patch(`/admin/users/${userId}/status`, { status }).then(unwrap),
};

export const notifications = {
  list: (unread) => api.get('/notifications', { params: unread ? { unread: true } : {} }).then(unwrap),
  markRead: (id) => api.patch(`/notifications/${id}/read`).then(unwrap),
};
