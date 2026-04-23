import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div class="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 w-full max-w-sm">
        <h1 class="text-xl font-bold text-gray-900 mb-6">Sign in</h1>

        <form (ngSubmit)="submit()" class="space-y-4">
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Email</label>
            <input [(ngModel)]="email" name="email" type="email" required
              class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="text-xs font-medium text-gray-600">Password</label>
              <a routerLink="/forgot-password" class="text-xs text-primary-600 hover:underline">Forgot password?</a>
            </div>
            <input [(ngModel)]="password" name="password" type="password" required
              class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>

          @if (error()) {
            <p class="text-xs text-danger-500">{{ error() }}</p>
          }

          <button type="submit" [disabled]="loading()"
            class="w-full py-2 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-700 disabled:opacity-50 transition-colors">
            {{ loading() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>

        <p class="text-center text-xs text-gray-400 mt-6">
          Don't have an account?
          <a routerLink="/register" class="text-primary-600 font-medium hover:underline">Register</a>
        </p>
      </div>
    </div>
  `,
})
export class LoginComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  email = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.login(this.email, this.password).subscribe({
      next: (res) => {
        this.auth.setToken(res.token);
        this.router.navigate(['/']);
      },
      error: (err: unknown) => {
        this.error.set(String(err));
        this.loading.set(false);
      },
    });
  }
}
