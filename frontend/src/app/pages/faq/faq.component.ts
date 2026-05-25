import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-faq',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="min-h-screen bg-gray-950">
      <div class="max-w-3xl mx-auto px-4 md:px-8 py-16 md:py-24">
        <div class="text-center mb-12">
          <h1 class="text-3xl md:text-5xl font-black text-white mb-3">Frequently asked questions</h1>
          <p class="text-gray-400">Everything you need to know about how TubeReturns works.</p>
        </div>

        <div class="divide-y divide-gray-800">
          @for (item of items; track item.q) {
            <div class="py-5">
              <button (click)="item.open = !item.open" class="w-full flex items-center justify-between gap-4 text-left group">
                <span class="text-sm md:text-base font-semibold text-white group-hover:text-green-400 transition-colors">{{ item.q }}</span>
                <svg class="w-5 h-5 text-gray-500 flex-shrink-0 transition-transform"
                     [class.rotate-180]="item.open"
                     fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
                </svg>
              </button>
              @if (item.open) {
                <p class="mt-3 text-sm text-gray-400 leading-relaxed">{{ item.a }}</p>
              }
            </div>
          }
        </div>

        <div class="mt-16 text-center border-t border-gray-800 pt-12">
          <p class="text-gray-400 text-sm mb-4">Still have questions?</p>
          <a routerLink="/contact"
            class="inline-flex items-center gap-2 px-5 py-2.5 bg-green-600 text-white rounded-lg text-sm font-semibold hover:bg-green-700 transition-colors">
            Contact us
          </a>
        </div>
      </div>
    </div>
  `,
})
export class FaqComponent {
  readonly items: { q: string; a: string; open: boolean }[] = [
    {
      q: 'What is TubeReturns?',
      a: 'TubeReturns is the first platform that objectively measures the real-money performance of finance YouTubers\' stock picks. We automatically discover videos, download transcripts, extract every stock recommendation using AI, and calculate actual returns from the day each pick was made — so you can see who is genuinely beating the market, not just who sounds the most convincing.',
      open: false,
    },
    {
      q: 'How does the AI extract stock picks from video transcripts?',
      a: 'Our pipeline downloads the full transcript of every video and sends it to a large language model trained to identify explicit buy recommendations. The model extracts the ticker symbol, company name, and the date the video was published, which we use as the entry date for performance tracking. Only clear, forward-looking buy calls are counted — vague mentions or educational discussions are excluded.',
      open: false,
    },
    {
      q: 'What does "alpha" mean and how is it calculated?',
      a: 'Alpha is the excess return a YouTuber\'s picks generated compared to simply buying the S&P 500 on the same day. For each pick we calculate the return from the video\'s publish date to today (or to the selected timeframe), then subtract the S&P 500\'s return over the same period. A positive alpha means the picks outperformed the index; negative alpha means they underperformed. All picks within a channel are equally weighted to produce a single channel alpha score.',
      open: false,
    },
    {
      q: 'How far back does the historical data go?',
      a: 'We track picks across the full publicly available history of each channel — some going back ten or more years. Stock price data and S&P 500 levels are pulled from the same historical dates, so every pick is measured on a truly apples-to-apples basis regardless of when the video was made.',
      open: false,
    },
    {
      q: 'How often is the data updated?',
      a: 'New videos are discovered daily. Stock prices and exchange rates are refreshed regularly so that return figures always reflect current market values. The leaderboard you see is live, not a static snapshot — rankings shift as markets move and new picks are added.',
      open: false,
    },
    {
      q: 'Do you track all types of stock picks, including short sells?',
      a: 'Currently TubeReturns tracks explicit buy recommendations. Short sell or "avoid" calls involve different risk dynamics and are tracked separately in our data model, with full leaderboard support planned for a future release.',
      open: false,
    },
    {
      q: 'How do you handle stocks traded in currencies other than USD?',
      a: 'All returns are converted to USD using historical exchange rates, so a pick on a London-listed stock is directly comparable to a pick on a NASDAQ stock. Our exchange-rate database covers every major currency and is refreshed alongside stock prices, ensuring currency conversions are historically accurate rather than based on today\'s rate.',
      open: false,
    },
    {
      q: 'Can I suggest a YouTuber who isn\'t on the leaderboard yet?',
      a: 'Yes — use the search bar in the top navigation to find any YouTube channel and submit it for tracking. Once submitted, our pipeline will discover the channel\'s full video history, download transcripts, and extract all stock picks automatically. Most channels are fully processed within a few hours, and you can opt in to receive an email notification when your suggested channel is ready.',
      open: false,
    },
    {
      q: 'What happens to a pick when a corporate event occurs — merger, acquisition, delisting, or ticker change?',
      a: 'Corporate events are one of the hardest problems in historical performance tracking, and we handle them differently from traditional data providers. When a stock is acquired, merges with another company, gets delisted, or changes its ticker symbol, conventional price feeds often drop the historical series entirely — making it impossible to know whether the original pick was profitable before the event. TubeReturns uses an AI-powered lookup layer: when our system detects that a ticker can no longer be resolved, it queries an AI model with the original company name and ticker to determine what happened — whether the stock was bought out at a known price, merged into a surviving entity we can still track, or genuinely went to zero. This means we can record the correct terminal return for the pick (e.g. the acquisition premium a shareholder would have received) rather than silently dropping it from the statistics. Picks that cannot be resolved even after AI lookup are flagged as "unknown" and reviewed manually through our admin tooling before being included in any channel\'s alpha calculation.',
      open: false,
    },
    {
      q: 'Is TubeReturns affiliated with any of the YouTubers it tracks?',
      a: 'No. TubeReturns is fully independent. We do not accept sponsorships from creators, do not receive compensation for rankings, and do not have any commercial relationships with any of the channels on the platform. Our only goal is to provide an objective, data-driven record of who has actually delivered returns — and who hasn\'t.',
      open: false,
    },
  ];
}
