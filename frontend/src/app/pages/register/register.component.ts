import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../api/api.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div class="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 w-full max-w-sm">
        @if (registered()) {
          <div class="text-center py-4">
            <div class="text-3xl mb-3">✉</div>
            <h1 class="text-xl font-bold text-gray-900 mb-2">Check your email</h1>
            <p class="text-sm text-gray-500">We sent a verification link to <strong>{{ email }}</strong>. Click it to activate your account.</p>
            <a routerLink="/login" class="block mt-6 text-sm text-primary-600 font-medium hover:underline">Back to sign in</a>
          </div>
        } @else {

        <h1 class="text-xl font-bold text-gray-900 mb-6">Create account</h1>

        <form (ngSubmit)="submit()" class="space-y-4">
          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">First name <span class="text-danger-500">*</span></label>
            <input [(ngModel)]="firstName" name="firstName" type="text" required autocomplete="given-name"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>

          <div>
            <label class="block text-xs font-medium text-gray-600 mb-1">Email <span class="text-danger-500">*</span></label>
            <input [(ngModel)]="email" name="email" type="email" required autocomplete="email"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>

          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="text-xs font-medium text-gray-600">Password <span class="text-danger-500">*</span></label>
              <button type="button" (click)="showPasswords.set(!showPasswords())"
                class="flex items-center gap-1 text-xs text-gray-400 hover:text-gray-600">
                @if (showPasswords()) {
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                  </svg>
                  Hide
                } @else {
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                    <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                  </svg>
                  Show
                }
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
            <div class="relative">
              <input [ngModel]="confirmPassword" (ngModelChange)="confirmPassword = $event; validateConfirm()" name="confirmPassword"
                [type]="showPasswords() ? 'text' : 'password'" required autocomplete="new-password"
                class="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                [class.border-gray-200]="!confirmError()"
                [class.border-danger-500]="confirmError()" />
            </div>
            @if (confirmError()) {
              <p class="text-xs text-danger-500 mt-1">{{ confirmError() }}</p>
            }
          </div>

          @if (error()) {
            <p class="text-xs text-danger-500">{{ error() }}</p>
          }

          <button type="submit" [disabled]="loading()"
            class="w-full py-2 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-700 disabled:opacity-50 transition-colors">
            {{ loading() ? 'Creating account…' : 'Create account' }}
          </button>
        </form>

        <p class="text-center text-xs text-gray-400 mt-6">
          Already have an account?
          <a routerLink="/login" class="text-primary-600 font-medium hover:underline">Sign in</a>
        </p>
        }
      </div>
    </div>
  `,
})
export class RegisterComponent {
  private readonly api = inject(ApiService);

  firstName = '';
  email = '';
  password = '';
  confirmPassword = '';
  readonly showPasswords = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly registered = signal(false);
  readonly passwordError = signal<string | null>(null);
  readonly confirmError = signal<string | null>(null);

  private static readonly PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,}$/;

  validatePassword(): void {
    if (!this.password) {
      this.passwordError.set(null);
      return;
    }
    this.passwordError.set(
      RegisterComponent.PASSWORD_PATTERN.test(this.password)
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
    this.api.register(this.firstName, this.email, this.password).subscribe({
      next: () => {
        this.registered.set(true);
      },
      error: (err: unknown) => {
        this.error.set(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
