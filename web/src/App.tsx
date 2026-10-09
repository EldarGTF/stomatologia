import { Route, Routes } from 'react-router';
import { Layout } from './components/Layout';
import { Booking } from './pages/Booking';
import { BookingDone } from './pages/BookingDone';
import { Doctors } from './pages/Doctors';
import { Home } from './pages/Home';
import { NotFound } from './pages/NotFound';
import { Privacy } from './pages/Privacy';
import { Services } from './pages/Services';

// Те же пути перечислены в SiteController на сервере — иначе обновление страницы даст 404.
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="services" element={<Services />} />
        <Route path="doctors" element={<Doctors />} />
        <Route path="booking" element={<Booking />} />
        <Route path="booking/:token" element={<BookingDone />} />
        <Route path="privacy" element={<Privacy />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
