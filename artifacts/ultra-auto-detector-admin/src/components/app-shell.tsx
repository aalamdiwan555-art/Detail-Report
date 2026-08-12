import { useState } from 'react';
import { Link, useLocation } from 'wouter';
import { LayoutDashboard, Users, ScanSearch, Activity, Settings, LogOut, Menu, X, ShieldCheck, Wifi } from 'lucide-react';
import { useGetCurrentUser, useHealthCheck, useLogout, getGetCurrentUserQueryKey } from '@workspace/api-client-react';
import { useQueryClient } from '@tanstack/react-query';

const nav = [
  { href: '/dashboard', label: 'Overview', icon: LayoutDashboard },
  { href: '/users', label: 'Accounts', icon: Users },
  { href: '/templates', label: 'Detection templates', icon: ScanSearch },
  { href: '/activity', label: 'Audit activity', icon: Activity },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const [location, setLocation] = useLocation();
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const current = useGetCurrentUser({ query: { queryKey: getGetCurrentUserQueryKey() } });
  const health = useHealthCheck({ query: { queryKey: ['/api/healthz'], refetchInterval: 30000 } });
  const logout = useLogout();
  const user = current.data?.user;

  const signOut = () => logout.mutate(undefined, { onSuccess: () => { queryClient.clear(); setLocation('/login'); } });
  return (
    <div className="min-h-[100dvh] bg-background app-grid">
      <aside className={`fixed inset-y-0 left-0 z-40 flex w-[262px] flex-col border-r border-[hsl(var(--border))] bg-[hsl(var(--card)/.94)] backdrop-blur-xl transition-transform duration-300 lg:translate-x-0 ${open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex h-[84px] items-center justify-between border-b border-border px-6">
          <Link href="/dashboard" className="flex items-center gap-3" data-testid="link-brand">
            <span className="grid h-10 w-10 place-items-center rounded-xl bg-primary text-primary-foreground shadow-sm"><ScanSearch size={21} strokeWidth={2.2} /></span>
            <span><span className="block text-[15px] font-bold tracking-[-.02em]">Ultra AutoDetector</span><span className="font-mono-ui block text-[9px] uppercase tracking-[.18em] text-muted-foreground">admin console</span></span>
          </Link>
          <button className="rounded-md p-1 text-muted-foreground lg:hidden" onClick={() => setOpen(false)} data-testid="button-close-navigation"><X size={19} /></button>
        </div>
        <div className="px-4 pt-7">
          <p className="mb-3 px-3 font-mono-ui text-[9px] font-bold uppercase tracking-[.2em] text-muted-foreground">Workspace</p>
          <nav className="space-y-1">
            {nav.map(({ href, label, icon: Icon }) => {
              const active = location === href;
              return <Link key={href} href={href} onClick={() => setOpen(false)} data-testid={`link-nav-${label.toLowerCase().replaceAll(' ', '-')}`} className={`group flex items-center gap-3 rounded-lg px-3 py-2.5 text-[13px] font-semibold transition-colors ${active ? 'bg-primary text-primary-foreground shadow-sm' : 'text-muted-foreground hover:bg-secondary hover:text-foreground'}`}><Icon size={17} className={active ? '' : 'opacity-75 group-hover:opacity-100'} /><span>{label}</span>{active && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-[hsl(var(--accent))]" />}</Link>;
            })}
          </nav>
          <p className="mb-3 mt-9 px-3 font-mono-ui text-[9px] font-bold uppercase tracking-[.2em] text-muted-foreground">System</p>
          <Link href="/settings" onClick={() => setOpen(false)} data-testid="link-nav-settings" className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-[13px] font-semibold transition-colors ${location === '/settings' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-secondary hover:text-foreground'}`}><Settings size={17} /><span>Settings</span></Link>
        </div>
        <div className="mt-auto p-4">
          <div className="mb-3 flex items-center gap-2 rounded-lg border border-border bg-secondary/60 px-3 py-2.5">
            <span className={`h-2 w-2 rounded-full ${health.isError ? 'bg-destructive' : 'bg-[hsl(var(--accent))] animate-pulse-soft'}`} />
            <span className="font-mono-ui text-[10px] uppercase tracking-[.1em] text-muted-foreground">{health.isError ? 'API offline' : 'API operational'}</span>
          </div>
          <div className="flex items-center gap-3 rounded-xl border border-border bg-background/70 p-3">
            <div className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-[hsl(var(--accent)/.18)] text-xs font-bold text-[hsl(var(--accent))]">{user?.email?.slice(0, 2).toUpperCase() ?? 'AD'}</div>
            <div className="min-w-0 flex-1"><p className="truncate text-xs font-bold" data-testid="text-sidebar-email">{user?.email ?? 'Administrator'}</p><p className="font-mono-ui text-[9px] uppercase tracking-wider text-muted-foreground">Administrator</p></div>
            <button onClick={signOut} disabled={logout.isPending} className="rounded-md p-1.5 text-muted-foreground hover:bg-secondary hover:text-destructive" data-testid="button-logout"><LogOut size={15} /></button>
          </div>
        </div>
      </aside>
      {open && <button className="fixed inset-0 z-30 bg-foreground/20 lg:hidden" onClick={() => setOpen(false)} aria-label="Close navigation" data-testid="button-navigation-overlay" />}
      <div className="lg:pl-[262px]">
        <header className="sticky top-0 z-20 flex h-[70px] items-center justify-between border-b border-border bg-[hsl(var(--background)/.83)] px-5 backdrop-blur-xl sm:px-8">
          <button className="rounded-lg border border-border bg-card p-2 text-muted-foreground lg:hidden" onClick={() => setOpen(true)} data-testid="button-open-navigation"><Menu size={18} /></button>
          <div className="hidden items-center gap-2 text-xs text-muted-foreground sm:flex"><ShieldCheck size={15} className="text-primary" /> Restricted operations workspace</div>
          <div className="ml-auto flex items-center gap-3 text-xs text-muted-foreground"><span className="hidden sm:block">UTC · {new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}</span><span className="h-1 w-1 rounded-full bg-[hsl(var(--accent))]" /><span className="font-semibold text-foreground">v0.2.0</span></div>
        </header>
        <main className="mx-auto max-w-[1440px] px-5 py-8 sm:px-8 lg:px-10">{children}</main>
      </div>
    </div>
  );
}

export function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description?: string; action?: React.ReactNode }) {
  return <div className="mb-8 flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><p className="mb-2 font-mono-ui text-[10px] font-bold uppercase tracking-[.22em] text-primary">{eyebrow}</p><h1 className="text-3xl font-bold tracking-[-.04em] text-foreground sm:text-[38px]">{title}</h1>{description && <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">{description}</p>}</div>{action}</div>;
}

export function LoadingRows({ count = 4 }: { count?: number }) { return <div className="space-y-3">{Array.from({ length: count }, (_, i) => <div key={i} className="h-16 animate-pulse rounded-xl bg-secondary/70" />)}</div>; }
export function ErrorState({ message = 'Unable to load this workspace view.', retry }: { message?: string; retry?: () => void }) { return <div className="rounded-2xl border border-destructive/20 bg-destructive/5 p-8 text-center"><p className="font-semibold">{message}</p>{retry && <button onClick={retry} className="mt-4 rounded-lg bg-foreground px-4 py-2 text-xs font-bold text-background" data-testid="button-retry">Try again</button>}</div>; }
export function EmptyState({ title, description, action }: { title: string; description: string; action?: React.ReactNode }) { return <div className="rounded-2xl border border-dashed border-border bg-card/60 p-12 text-center"><div className="mx-auto mb-4 grid h-11 w-11 place-items-center rounded-full bg-secondary text-primary"><Wifi size={19} /></div><p className="font-semibold">{title}</p><p className="mx-auto mt-2 max-w-sm text-sm text-muted-foreground">{description}</p>{action && <div className="mt-5">{action}</div>}</div>; }
