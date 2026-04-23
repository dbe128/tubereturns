import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="bg-white border-b border-gray-200 sticky top-0 z-10">
      <div class="px-6 h-[3.33rem] flex items-center justify-between">
        <a routerLink="/">
          <img src="logo.png" alt="TubeReturns" class="h-36 rounded" />
        </a>
        <div class="flex items-center gap-3">
          @if (auth.isAuthenticated) {
            <span class="text-sm text-gray-500">Welcome, {{ auth.user()?.firstName }}</span>
            <button (click)="logout()"
              class="px-3 py-1.5 text-sm text-gray-600 hover:text-gray-900 transition-colors">
              Sign out
            </button>
          } @else {
            <a routerLink="/login"
              class="px-3 py-1.5 text-sm text-gray-600 hover:text-gray-900 transition-colors">
              Sign in
            </a>
            <a routerLink="/register"
              class="px-3 py-1.5 bg-gray-900 text-white rounded-lg text-sm font-semibold hover:bg-gray-700 transition-colors">
              Register
            </a>
          }
        </div>
      </div>
    </header>
  `,
})
export class NavbarComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
