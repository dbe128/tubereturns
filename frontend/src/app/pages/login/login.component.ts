import { Component, inject, signal, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../services/auth.service';

type GoogleApi = {
  accounts: {
    id: {
      initialize(config: { client_id: string; callback: (r: { credential: string }) => void }): void;
      renderButton(el: HTMLElement, opts: { theme: string; size: string; width?: number }): void;
    };
  };
};

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-800 flex items-center justify-center px-4">
      <div class="bg-white rounded-2xl shadow-sm border border-gray-200 p-6 sm:p-8 w-full max-w-sm">
        <h1 class="text-xl font-bold text-gray-900 mb-6">{{ heading }}</h1>

        <div id="google-signin-btn" class="w-full min-h-[44px] mb-4"></div>

        <div class="relative flex items-center mb-4">
          <div class="flex-1 border-t border-gray-200"></div>
          <span class="px-3 text-xs text-gray-400">or</span>
          <div class="flex-1 border-t border-gray-200"></div>
        </div>

        <form (ngSubmit)="submit()" class="space-y-4">
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Email</label>
            <input [(ngModel)]="email" name="email" type="email" required autocomplete="email"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="text-xs font-medium text-gray-600">Password</label>
              <a routerLink="/forgot-password" class="text-xs text-primary-600 hover:underline">Forgot password?</a>
            </div>
            <input [(ngModel)]="password" name="password" type="password" required autocomplete="current-password"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>

          @if (error()) {
            <p class="text-xs text-danger-500">{{ error() }}</p>
          }

          <button type="submit" [disabled]="loading()"
            class="w-full py-2 bg-green-600 text-white rounded-lg text-sm font-semibold hover:bg-green-700 disabled:opacity-50 transition-colors">
            {{ loading() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>

        <p class="text-center text-xs text-gray-400 mt-6">
          Don't have an account?
          <a [routerLink]="['/signup']" [queryParams]="returnUrl ? { returnUrl } : {}" class="text-primary-600 font-medium hover:underline">Sign up</a>
        </p>
      </div>
    </div>
  `,
})
export class LoginComponent implements AfterViewInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  email = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  get returnUrl(): string {
    return this.route.snapshot.queryParamMap.get('returnUrl') ?? '';
  }

  get heading(): string {
    if (this.returnUrl) return 'Log in to access this feature. You will be redirected afterwards';
    if (localStorage.getItem('pendingAddChannel')) return 'Log in to suggest a channel';
    return 'Sign in';
  }

  ngAfterViewInit(): void {
    const google = (window as Window & { google?: GoogleApi }).google;
    if (!google) { return; }
    const btn = document.getElementById('google-signin-btn');
    if (!btn) { return; }
    google.accounts.id.initialize({
      client_id: '869730842488-j309dpfmbclhi6hrg2eavisn75i7fbt6.apps.googleusercontent.com',
      callback: (r) => this.handleGoogleCredential(r.credential),
    });
    google.accounts.id.renderButton(btn, { theme: 'outline', size: 'large', width: btn.offsetWidth || 344 });
  }

  handleGoogleCredential(credential: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.googleLogin(credential).subscribe({
      next: (res) => {
        this.auth.setToken(res.token);
        const target = this.returnUrl || localStorage.getItem('pendingReturnUrl') || '/';
        localStorage.removeItem('pendingReturnUrl');
        this.router.navigateByUrl(target);
      },
      error: (err: unknown) => {
        this.error.set(String(err));
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.login(this.email, this.password).subscribe({
      next: (res) => {
        this.auth.setToken(res.token);
        const target = this.returnUrl || localStorage.getItem('pendingReturnUrl') || '/';
        localStorage.removeItem('pendingReturnUrl');
        this.router.navigateByUrl(target);
      },
      error: (err: unknown) => {
        this.error.set(String(err));
        this.loading.set(false);
      },
    });
  }
}
