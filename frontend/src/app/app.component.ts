import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <div class="min-h-screen bg-gray-950 flex flex-col">
      <app-navbar class="sticky top-0 z-10 block" />
      <main class="flex-1">
        <router-outlet />
      </main>
      <footer class="bg-gray-900 border-t border-gray-800 py-4 text-center text-xs text-gray-500">
        © 2026 <span class="text-red-600 font-semibold">Tube</span><span class="text-green-500 font-semibold">Returns</span>.com. All rights reserved.
      </footer>
    </div>
  `,
})
export class AppComponent {}
