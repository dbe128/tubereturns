import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="min-h-screen bg-gray-950 flex items-start justify-center px-4 py-16 md:py-24">
      <div class="w-full max-w-lg">
        <div class="text-center mb-10">
          <h1 class="text-3xl md:text-4xl font-black text-white mb-3">Contact us</h1>
          <p class="text-gray-400 text-sm leading-relaxed">Have a question, suggestion, or found something off? We'd love to hear from you.<br>You'll get a copy of your message sent to your inbox.</p>
        </div>

        @if (sent()) {
          <div class="bg-green-900/30 border border-green-700 rounded-2xl p-8 text-center">
            <div class="text-8xl mb-4 text-white">✉</div>
            <h2 class="text-lg font-bold text-white mb-2">Message sent!</h2>
            <p class="text-sm text-gray-400">Thanks for reaching out. We've sent a copy to <strong class="text-white">{{ auth.user()?.email }}</strong> and will get back to you as soon as we can.</p>
          </div>
        } @else {
          <form (ngSubmit)="submit()" class="bg-gray-900 border border-gray-700 rounded-2xl p-6 md:p-8 space-y-5">
            <div>
              <label class="block text-xs font-medium text-gray-400 mb-1">Message <span class="text-red-500">*</span></label>
              <textarea [(ngModel)]="message" name="message" required rows="8"
                class="w-full bg-gray-800 border border-gray-700 text-white rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500 placeholder-gray-600 resize-none"
                placeholder="Tell us what's on your mind…"></textarea>
            </div>
            @if (error()) {
              <p class="text-xs text-red-400">{{ error() }}</p>
            }
            <button type="submit" [disabled]="loading()"
              class="w-full py-2.5 bg-green-600 text-white rounded-lg text-sm font-semibold hover:bg-green-700 disabled:opacity-50 transition-colors">
              {{ loading() ? 'Sending…' : 'Send message' }}
            </button>
          </form>
        }
      </div>
    </div>
  `,
})
export class ContactComponent {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);

  message = '';

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly sent = signal(false);

  submit(): void {
    if (this.message.trim().length < 10) {
      this.error.set('Message must be at least 10 characters.');
      return;
    }
    const user = this.auth.user();
    const name = [user?.firstName, user?.lastName].filter(Boolean).join(' ') || 'Unknown';
    const email = user?.email ?? '';
    this.loading.set(true);
    this.error.set(null);
    this.api.sendContact(name, email, this.message.trim()).subscribe({
      next: () => {
        this.sent.set(true);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
