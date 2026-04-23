import { Injectable, signal } from '@angular/core';

export interface AuthUser {
  email: string;
  firstName: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'auth_token';

  readonly user = signal<AuthUser | null>(this.loadFromStorage());

  get isAuthenticated(): boolean {
    return this.user() !== null;
  }

  setToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    this.user.set(this.decodeToken(token));
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this.user.set(null);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private loadFromStorage(): AuthUser | null {
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (!token) return null;
    return this.decodeToken(token);
  }

  private decodeToken(token: string): AuthUser | null {
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
      const payload = JSON.parse(new TextDecoder().decode(bytes));
      if (payload.exp * 1000 < Date.now()) {
        localStorage.removeItem(this.TOKEN_KEY);
        return null;
      }
      return { email: payload.sub, firstName: payload.firstName };
    } catch {
      return null;
    }
  }
}
