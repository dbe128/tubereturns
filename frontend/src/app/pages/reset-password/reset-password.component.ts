import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../api/api.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div class="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 w-full max-w-sm">
        @if (status() === 'success') {
          <div class="text-center py-4">
            <div class="text-3xl mb-3">✓</div>
            <h1 class="text-xl font-bold text-gray-900 mb-2">Password updated</h1>
            <p class="text-sm text-gray-500">Your password has been reset. You can now sign in with your new password.</p>
            <a routerLink="/login" class="block mt-6 text-sm text-primary-600 font-medium hover:underline">Sign in</a>
          </div>
        } @else if (status() === 'invalid') {
          <div class="text-center py-4">
            <div class="text-3xl mb-3">✕</div>
            <h1 class="text-xl font-bold text-gray-900 mb-2">Link invalid or expired</h1>
            <p class="text-sm text-gray-500">This reset link is no longer valid. Please request a new one.</p>
            <a routerLink="/forgot-password" class="block mt-6 text-sm text-primary-600 font-medium hover:underline">Request new link</a>
          </div>
        } @else {
          <h1 class="text-xl font-bold text-gray-900 mb-6">Set new password</h1>

          <form (ngSubmit)="submit()" class="space-y-4">
            <div>
              <div class="flex items-center justify-between mb-1">
                <label class="text-xs font-medium text-gray-600">New password <span class="text-danger-500">*</span></label>
                <button type="button" (click)="showPasswords.set(!showPasswords())"
                  class="text-xs text-gray-400 hover:text-gray-600">
                  {{ showPasswords() ? 'Hide' : 'Show' }}
                </button>
              </div>
              <input [ngModel]="password" (ngModelChange)="password = $event; validatePassword()" name="password"
                [type]="showPasswords() ? 'text' : 'password'" required autocomplete="new-password"
                class="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                [class.border-gray-200]="!passwordError()"
                [class.border-danger-500]="passwordError()" />
              @if (passwordError()) {
                <p class="text-xs text-danger-500 mt-1">{{ passwordError() }}</p>
              } @else {
                <p class="text-xs text-gray-400 mt-1">Min 8 chars — uppercase, lowercase, digit and special character.</p>
              }
            </div>

            <div>
              <label class="block text-xs font-medium text-gray-600 mb-1">Confirm password <span class="text-danger-500">*</span></label>
              <input [ngModel]="confirmPassword" (ngModelChange)="confirmPassword = $event; validateConfirm()" name="confirmPassword"
                [type]="showPasswords() ? 'text' : 'password'" required autocomplete="new-password"
                class="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                [class.border-gray-200]="!confirmError()"
                [class.border-danger-500]="confirmError()" />
              @if (confirmError()) {
                <p class="text-xs text-danger-500 mt-1">{{ confirmError() }}</p>
              }
            </div>

            @if (error()) {
              <p class="text-xs text-danger-500">{{ error() }}</p>
            }

            <button type="submit" [disabled]="loading()"
              class="w-full py-2 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-700 disabled:opacity-50 transition-colors">
              {{ loading() ? 'Updating…' : 'Update password' }}
            </button>
          </form>
        }
      </div>
    </div>
  `,
})
export class ResetPasswordComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  private token = '';
  password = '';
  confirmPassword = '';
  readonly showPasswords = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly passwordError = signal<string | null>(null);
  readonly confirmError = signal<string | null>(null);
  readonly status = signal<'form' | 'success' | 'invalid'>('form');

  private static readonly PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,}$/;

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';
    if (!this.token) {
      this.status.set('invalid');
    }
  }

  validatePassword(): void {
    if (!this.password) {
      this.passwordError.set(null);
      return;
    }
    this.passwordError.set(
      ResetPasswordComponent.PASSWORD_PATTERN.test(this.password)
        ? null
        : 'Password must be at least 8 characters and include uppercase, lowercase, a digit and a special character',
    );
    if (this.confirmPassword) this.validateConfirm();
  }

  validateConfirm(): void {
    this.confirmError.set(
      this.confirmPassword && this.password !== this.confirmPassword
        ? 'Passwords do not match'
        : null,
    );
  }

  submit(): void {
    this.validatePassword();
    this.validateConfirm();
    if (this.passwordError() || this.confirmError()) return;

    this.loading.set(true);
    this.error.set(null);
    this.api.resetPassword(this.token, this.password).subscribe({
      next: () => {
        this.status.set('success');
      },
      error: (err: unknown) => {
        const msg = err instanceof Error ? err.message : 'Something went wrong. Please try again.';
        if (msg.toLowerCase().includes('invalid') || msg.toLowerCase().includes('expired')) {
          this.status.set('invalid');
        } else {
          this.error.set(msg);
          this.loading.set(false);
        }
      },
    });
  }
}
