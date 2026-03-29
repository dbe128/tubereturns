import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="bg-white border-b border-gray-200 sticky top-0 z-10">
      <div class="px-6 h-[3.33rem] flex items-center">
        <a routerLink="/">
          <img src="logo.png" alt="TubeReturns" class="h-36 rounded" />
        </a>
      </div>
    </header>
  `,
})
export class NavbarComponent {}
