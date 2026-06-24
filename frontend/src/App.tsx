import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppShell from './layout/AppShell'
import Overview from './pages/Overview'
import SitesIndex from './pages/SitesIndex'
import SiteDetail from './pages/SiteDetail'
import DeviceDetail from './pages/DeviceDetail'
import StationDetail from './pages/StationDetail'
import AuditLog from './pages/AuditLog'
import AskAI from './pages/AskAI'
import Reports from './pages/Reports'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<AppShell />}>
          <Route index element={<Overview />} />
          <Route path="sites" element={<SitesIndex />} />
          <Route path="sites/:siteId" element={<SiteDetail />} />
          <Route path="sites/:siteId/devices/:deviceId" element={<DeviceDetail />} />
          <Route path="sites/:siteId/stations/:station" element={<StationDetail />} />
          <Route path="audit" element={<AuditLog />} />
          <Route path="ask" element={<AskAI />} />
          <Route path="reports" element={<Reports />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
