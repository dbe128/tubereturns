import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../api/api.service';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div class="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 w-full max-w-sm text-center">
        @if (status() === 'loading') {
          <div class="flex justify-center py-4">
            <div class="animate-spin rounded-full h-8 w-8 border-2 border-primary-500 border-t-transparent"></div>
          </div>
          <p class="text-sm text-gray-500 mt-4">Verifying your email…</p>
        } @else if (status() === 'success') {
          <div class="text-4xl mb-4 text-primary-600">✓</div>
          <h1 class="text-xl font-bold text-gray-900 mb-2">Email verified!</h1>
          <p class="text-sm text-gray-500 mb-6">Your account is now active. You can sign in.</p>
          <a routerLink="/login"
            class="block w-full py-2 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-700 transition-colors">
            Sign in
          </a>
        } @else {
          <div class="text-4xl mb-4 text-danger-500">✗</div>
          <h1 class="text-xl font-bold text-gray-900 mb-2">Verification failed</h1>
          <p class="text-sm text-gray-500 mb-6">{{ error() }}</p>
          <a routerLink="/register"
            class="block w-full py-2 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-700 transition-colors">
            Register again
          </a>
        }
      </div>
    </div>
  `,
})
export class VerifyEmailComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  readonly status = signal<'loading' | 'success' | 'error'>('loading');
  readonly error = signal<string>('Verification failed. The link may have expired.');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.error.set('No verification token provided.');
      this.status.set('error');
      return;
    }
    this.api.verifyEmail(token).subscribe({
      next: () => this.status.set('success'),
      error: (err: unknown) => {
        this.error.set(err instanceof Error ? err.message : 'Verification failed. The link may have expired.');
        this.status.set('error');
      },
    });
  }
}
