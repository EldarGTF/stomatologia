import '@fontsource-variable/unbounded';
import '@fontsource-variable/onest';
import './styles.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './App';
import { ClinicProvider } from './hooks';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ClinicProvider>
        <App />
      </ClinicProvider>
    </BrowserRouter>
  </StrictMode>,
);
