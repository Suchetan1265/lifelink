import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import RequireRole from './auth/RequireRole';
import Landing from './pages/Landing';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import CompleteProfile from './pages/donor/CompleteProfile';
import DonorDashboard from './pages/donor/DonorDashboard';
import DonationHistory from './pages/donor/DonationHistory';
import Opportunities from './pages/donor/Opportunities';
import RequestList from './pages/hospital/RequestList';
import RaiseRequest from './pages/hospital/RaiseRequest';
import RequestDetail from './pages/hospital/RequestDetail';
import Escalations from './pages/bank/Escalations';
import Inventory from './pages/bank/Inventory';
import Verifications from './pages/admin/Verifications';
import Stats from './pages/admin/Stats';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />

      <Route element={<RequireRole roles={['DONOR']} />}>
        {/* Outside the shell: there is no dashboard to show until it is done. */}
        <Route path="/donor/complete-profile" element={<CompleteProfile />} />
        <Route element={<Layout />}>
          <Route path="/donor" element={<DonorDashboard />} />
          <Route path="/donor/opportunities" element={<Opportunities />} />
          <Route path="/donor/history" element={<DonationHistory />} />
        </Route>
      </Route>

      <Route element={<RequireRole roles={['HOSPITAL']} />}>
        <Route element={<Layout />}>
          <Route path="/hospital/requests" element={<RequestList />} />
          <Route path="/hospital/requests/new" element={<RaiseRequest />} />
          <Route path="/hospital/requests/:id" element={<RequestDetail />} />
        </Route>
      </Route>

      <Route element={<RequireRole roles={['BLOOD_BANK']} />}>
        <Route element={<Layout />}>
          <Route path="/bank/escalations" element={<Escalations />} />
          <Route path="/bank/inventory" element={<Inventory />} />
        </Route>
      </Route>

      <Route element={<RequireRole roles={['ADMIN']} />}>
        <Route element={<Layout />}>
          <Route path="/admin/verifications" element={<Verifications />} />
          <Route path="/admin/stats" element={<Stats />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
