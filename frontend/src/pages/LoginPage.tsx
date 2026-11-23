import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Landmark } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { Alert, Field } from '../components/ui';

const DEMO = [
  { label: 'Ana (cliente)', email: 'ana.garcia@banca.demo' },
  { label: 'Luis (cliente)', email: 'luis.perez@banca.demo' },
  { label: 'Operador', email: 'operador@banca.demo' },
  { label: 'Admin', email: 'admin@banca.demo' },
];
const DEMO_PASSWORD = 'Demo1234!';

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={from} replace />;

  async function doLogin(e: string, p: string) {
    setBusy(true);
    setError(null);
    try {
      await login(e, p);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo iniciar sesión');
    } finally {
      setBusy(false);
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    doLogin(email, password);
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <span className="brand-logo lg"><Landmark size={26} /></span>
        <h1>Banca Demo</h1>
        <p className="muted">Simulador de operaciones bancarias · datos y dinero ficticios</p>
        <form className="form" onSubmit={submit}>
          {error && <Alert>{error}</Alert>}
          <Field label="Email">
            <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </Field>
          <Field label="Contraseña">
            <input type="password" autoComplete="current-password" value={password}
              onChange={(e) => setPassword(e.target.value)} required />
          </Field>
          <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Entrando…' : 'Iniciar sesión'}</button>
        </form>
        <div className="demo-accounts">
          <p className="muted small">Entrar con una cuenta de demostración:</p>
          <div className="demo-buttons">
            {DEMO.map((d) => (
              <button key={d.email} className="btn btn-soft btn-sm" disabled={busy} onClick={() => doLogin(d.email, DEMO_PASSWORD)}>
                {d.label}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
