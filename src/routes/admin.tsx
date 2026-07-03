import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState, type FormEvent } from "react";
import { Eye, EyeOff } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";

export const Route = createFileRoute("/admin")({
  ssr: false,
  component: AdminPage,
  head: () => ({
    meta: [{ title: "Admin · AETHERX" }, { name: "robots", content: "noindex" }],
  }),
});

type TesterEmail = { id: string; email: string; created_at: string };

function AdminPage() {
  const [loading, setLoading] = useState(true);
  const [userEmail, setUserEmail] = useState<string | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [emails, setEmails] = useState<TesterEmail[]>([]);
  const [error, setError] = useState<string | null>(null);

  // Login form
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  async function refresh() {
    setLoading(true);
    setError(null);
    const { data: sess } = await supabase.auth.getSession();
    const user = sess.session?.user ?? null;
    setUserEmail(user?.email ?? null);
    if (!user) {
      setIsAdmin(false);
      setEmails([]);
      setLoading(false);
      return;
    }
    const { data: roles } = await supabase
      .from("user_roles")
      .select("role")
      .eq("user_id", user.id);
    const admin = !!roles?.some((r) => r.role === "admin");
    setIsAdmin(admin);
    if (admin) {
      const { data, error } = await supabase
        .from("tester_emails")
        .select("*")
        .order("created_at", { ascending: false });
      if (error) setError(error.message);
      else setEmails((data as TesterEmail[]) ?? []);
    }
    setLoading(false);
  }

  useEffect(() => {
    refresh();
    const { data: sub } = supabase.auth.onAuthStateChange(() => refresh());
    return () => sub.subscription.unsubscribe();
  }, []);

  async function handleAuth(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (mode === "signup") {
        const { error } = await supabase.auth.signUp({
          email,
          password,
          options: { emailRedirectTo: window.location.origin + "/admin" },
        });
        if (error) throw error;
      } else {
        const { error } = await supabase.auth.signInWithPassword({ email, password });
        if (error) throw error;
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Error";
      // Silenciar el rate limit de Supabase: intentar login automáticamente
      if (/security purposes|rate limit|after \d+ seconds?/i.test(msg)) {
        const { error: loginErr } = await supabase.auth.signInWithPassword({ email, password });
        if (loginErr && !/security purposes|rate limit|after \d+ seconds?/i.test(loginErr.message)) {
          setError(loginErr.message);
        }
      } else {
        setError(msg);
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSignOut() {
    await supabase.auth.signOut();
  }

  function exportCsv() {
    const csv =
      "email,created_at\n" +
      emails.map((e) => `${e.email},${e.created_at}`).join("\n");
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "tester_emails.csv";
    a.click();
    URL.revokeObjectURL(url);
  }

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center text-white/60 text-sm">
        Cargando…
      </div>
    );
  }

  if (!userEmail) {
    return (
      <div className="min-h-screen flex items-center justify-center px-6">
        <form
          onSubmit={handleAuth}
          className="glass-card w-full max-w-sm rounded-3xl p-8 space-y-4"
        >
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
              Panel privado
            </p>
            <h1 className="mt-1 text-2xl font-bold text-display text-ice-white">
              Admin AETHERX
            </h1>
          </div>
          <input
            type="email"
            required
            placeholder="tu@correo.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-xl bg-white/5 border border-white/10 px-4 py-3 text-sm text-white outline-none focus:border-electric-blue"
          />
          <div className="relative">
            <input
              type={showPassword ? "text" : "password"}
              required
              minLength={6}
              autoComplete="new-password"
              placeholder="Contraseña (mín. 6)"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="no-native-reveal w-full rounded-xl bg-white/5 border border-white/10 px-4 py-3 pr-11 text-sm text-white outline-none focus:border-electric-blue"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={showPassword ? "Ocultar contraseña" : "Mostrar contraseña"}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-white/50 hover:text-white transition"
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          {error && <p className="text-xs text-red-400">{error}</p>}
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-full bg-electric-blue px-5 py-3 text-xs font-bold uppercase tracking-[0.2em] text-space-black hover:bg-ocean-cyan transition disabled:opacity-50"
          >
            {submitting ? "…" : mode === "login" ? "Entrar" : "Crear cuenta"}
          </button>
          <button
            type="button"
            onClick={() => setMode(mode === "login" ? "signup" : "login")}
            className="w-full text-xs text-white/50 hover:text-white transition"
          >
            {mode === "login" ? "Crear cuenta" : "Ya tengo cuenta"}
          </button>
          <Link to="/" className="block text-center text-[10px] uppercase tracking-[0.2em] text-white/40 hover:text-white">
            ← Volver
          </Link>
        </form>
      </div>
    );
  }

  if (!isAdmin) {
    return (
      <div className="min-h-screen flex items-center justify-center px-6">
        <div className="glass-card max-w-sm rounded-3xl p-8 text-center space-y-4">
          <h1 className="text-xl font-bold text-display text-ice-white">Acceso restringido</h1>
          <p className="text-sm text-white/60">
            Tu cuenta <strong>{userEmail}</strong> no tiene rol de administrador.
          </p>
          <p className="text-xs text-white/40">
            Pide al dueño del proyecto que añada tu usuario a <code>user_roles</code> con rol <code>admin</code>.
          </p>
          <button
            onClick={handleSignOut}
            className="rounded-full border border-white/15 px-5 py-2.5 text-xs font-bold uppercase tracking-[0.2em] text-white hover:bg-white/5 transition"
          >
            Cerrar sesión
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen px-6 py-10 max-w-3xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-[0.3em] text-electric-blue">
            Admin
          </p>
          <h1 className="mt-1 text-3xl font-bold text-display text-ice-white">
            Testers registrados
          </h1>
          <p className="text-xs text-white/40 mt-1">{userEmail}</p>
        </div>
        <button
          onClick={handleSignOut}
          className="rounded-full border border-white/15 px-4 py-2 text-[10px] font-bold uppercase tracking-[0.2em] text-white hover:bg-white/5 transition"
        >
          Salir
        </button>
      </div>

      <div className="glass-card rounded-3xl p-6">
        <div className="flex items-center justify-between mb-4">
          <p className="text-sm text-white/80">
            Total: <span className="font-bold text-electric-blue">{emails.length}</span>
          </p>
          <button
            onClick={exportCsv}
            disabled={!emails.length}
            className="rounded-full bg-electric-blue px-4 py-2 text-[10px] font-bold uppercase tracking-[0.2em] text-space-black hover:bg-ocean-cyan transition disabled:opacity-40"
          >
            Exportar CSV
          </button>
        </div>

        {error && <p className="text-xs text-red-400 mb-3">{error}</p>}

        {emails.length === 0 ? (
          <p className="text-sm text-white/50 text-center py-8">
            Aún no hay registros.
          </p>
        ) : (
          <div className="divide-y divide-white/5">
            {emails.map((e, i) => (
              <div key={e.id} className="flex items-center justify-between py-3">
                <div className="flex items-center gap-3 min-w-0">
                  <span className="text-[10px] font-mono text-white/30 w-6">{i + 1}</span>
                  <span className="text-sm text-white truncate">{e.email}</span>
                </div>
                <span className="text-[10px] text-white/40 whitespace-nowrap ml-3">
                  {new Date(e.created_at).toLocaleString()}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
