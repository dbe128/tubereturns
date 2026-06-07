import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'app-privacy-policy',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="min-h-screen bg-gray-950">
      <div class="max-w-3xl mx-auto px-4 md:px-8 py-16 md:py-24">
        <div class="mb-12">
          <h1 class="text-3xl md:text-5xl font-black text-white mb-3">Privacy Policy</h1>
          <p class="text-gray-400 text-sm">Last updated: June 2025</p>
        </div>

        <div class="space-y-10 text-gray-300 text-sm leading-relaxed">

          <section>
            <h2 class="text-lg font-bold text-white mb-3">1. Who we are</h2>
            <p>TubeReturns (<strong class="text-white">tubereturns.com</strong>) is an independent platform that tracks the historical stock-pick performance of finance YouTubers. For any privacy-related enquiries contact us at <a href="mailto:hello@tubereturns.com" class="text-blue-400 hover:underline">hello@tubereturns.com</a>.</p>
          </section>

          <section>
            <h2 class="text-lg font-bold text-white mb-3">2. Data we collect and why</h2>
            <p class="mb-2">We apply a data-minimisation approach and collect only what is strictly necessary:</p>
            <ul class="list-disc list-inside space-y-2 text-gray-400">
              <li><span class="text-gray-200">Email address</span> — collected when you register or opt in to channel-ready notifications. Legal basis: contract performance / your explicit consent.</li>
              <li><span class="text-gray-200">Session token (JWT)</span> — stored in your browser's <span class="font-mono text-gray-200">localStorage</span> to keep you signed in. It is never shared with third parties.</li>
              <li><span class="text-gray-200">Anonymous usage analytics</span> — aggregated, non-identifiable page-view data used to improve the service. No personal identifiers are recorded.</li>
            </ul>
            <p class="mt-3">We do not sell, rent, or trade your personal data. We do not use it for advertising profiling.</p>
          </section>

          <section>
            <h2 class="text-lg font-bold text-white mb-3">3. Cookies</h2>
            <p>TubeReturns does not use tracking or advertising cookies. The only browser storage we use is <span class="font-mono text-gray-200">localStorage</span> for the authentication token described above. You can clear it at any time via your browser settings.</p>
          </section>

          <section>
            <h2 class="text-lg font-bold text-white mb-3">4. Affiliate and referral links</h2>
            <p>Some links on this site are affiliate or referral links. If you follow one and make a purchase or open an account, we may receive a commission at no additional cost to you. These relationships do not influence rankings, data, or any editorial content on TubeReturns.</p>
          </section>

          <section>
            <h2 class="text-lg font-bold text-white mb-3">5. Investment disclaimer</h2>
            <p>TubeReturns is not a registered investment adviser. All content — including channel rankings, pick data, and return calculations — is provided for informational and entertainment purposes only and does not constitute financial advice. Past performance does not guarantee future results. Always conduct your own research before making investment decisions.</p>
          </section>

          <section>
            <h2 class="text-lg font-bold text-white mb-3">6. Your rights (GDPR)</h2>
            <p class="mb-2">If you are in the European Economic Area you have the following rights regarding your personal data:</p>
            <ul class="list-disc list-inside space-y-1 text-gray-400">
              <li><span class="text-gray-200">Access</span> — request a copy of the data we hold about you.</li>
              <li><span class="text-gray-200">Rectification</span> — ask us to correct inaccurate data.</li>
              <li><span class="text-gray-200">Erasure</span> — request deletion of your account and all associated personal data.</li>
              <li><span class="text-gray-200">Portability</span> — receive your data in a structured, machine-readable format.</li>
              <li><span class="text-gray-200">Objection / restriction</span> — object to or restrict certain processing activities.</li>
              <li><span class="text-gray-200">Withdraw consent</span> — unsubscribe from notifications or delete your account at any time.</li>
            </ul>
            <p class="mt-3">To exercise any of these rights, email <a href="mailto:hello@tubereturns.com" class="text-blue-400 hover:underline">hello@tubereturns.com</a>. We will respond within 30 days.</p>
          </section>

          <section>
            <h2 class="text-lg font-bold text-white mb-3">7. Data retention</h2>
            <p>We retain your email address and account data for as long as your account is active. If you request deletion, we will remove your personal data within 30 days, except where retention is required by law.</p>
          </section>

          <section>
            <h2 class="text-lg font-bold text-white mb-3">8. Changes to this policy</h2>
            <p>We may update this policy from time to time. Material changes will be communicated via email to registered users. The "Last updated" date at the top of this page will always reflect the most recent revision.</p>
          </section>

        </div>

        <div class="mt-16 border-t border-gray-800 pt-8">
          <a routerLink="/" class="text-sm text-gray-400 hover:text-white transition-colors">← Back to leaderboard</a>
        </div>
      </div>
    </div>
  `,
})
export class PrivacyPolicyComponent {
  constructor() { inject(Title).setTitle('Privacy Policy | TubeReturns'); }
}
