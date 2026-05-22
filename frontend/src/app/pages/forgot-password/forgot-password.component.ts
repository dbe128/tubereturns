import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../api/api.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-800 flex items-center justify-center px-4">
      <div class="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 w-full max-w-sm">
        @if (submitted()) {
          <div class="text-center py-4">
            <div class="text-7xl mb-4">✉</div>
            <h1 class="text-xl font-bold text-gray-900 mb-2">Check your email</h1>
            <p class="text-sm text-gray-500">If <strong>{{ email }}</strong> is registered, we sent a password reset link. Check your inbox.</p>
            <a routerLink="/login" class="block mt-6 text-sm text-primary-600 font-medium hover:underline">Back to sign in</a>
          </div>
        } @else {
          <h1 class="text-xl font-bold text-gray-900 mb-2">Forgot password?</h1>
          <p class="text-sm text-gray-500 mb-6">Enter your email and we'll send you a reset link.</p>

          <form (ngSubmit)="submit()" class="space-y-4">
            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Email</label>
              <input [(ngModel)]="email" name="email" type="email" required autofocus autocomplete="email"
                class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>

            @if (error()) {
              <p class="text-xs text-danger-500">{{ error() }}</p>
            }

            <button type="submit" [disabled]="loading()"
              class="w-full py-2 bg-green-600 text-white rounded-lg text-sm font-semibold hover:bg-green-700 disabled:opacity-50 transition-colors">
              {{ loading() ? 'Sending…' : 'Send reset link' }}
            </button>
          </form>

          <p class="text-center text-xs text-gray-400 mt-6">
            <a routerLink="/login" class="text-primary-600 font-medium hover:underline">Back to sign in</a>
          </p>
        }
      </div>
    </div>
  `,
})
export class ForgotPasswordComponent {
  private readonly api = inject(ApiService);

  email = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitted = signal(false);

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.forgotPassword(this.email).subscribe({
      next: () => {
        this.submitted.set(true);
      },
      error: (err: unknown) => {
        this.error.set(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
