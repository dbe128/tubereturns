import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar.component';
import { FeatureFlagService } from './services/feature-flag.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-950 flex flex-col">
      <app-navbar class="sticky top-0 z-10 block" />
      <main class="flex-1">
        <router-outlet />
      </main>
      <footer class="bg-gray-900 border-t border-gray-800 py-4 text-center text-xs text-gray-500">
        © 2026 TubeReturns.com. All rights reserved.
        <span class="mx-2">·</span>
        <a routerLink="/privacy" class="hover:text-gray-300 underline underline-offset-2 transition-colors">Privacy Policy</a>
      </footer>
    </div>
  `,
})
export class AppComponent implements OnInit {
  private readonly featureFlags = inject(FeatureFlagService);

  ngOnInit(): void {
    this.featureFlags.load();
  }
}
