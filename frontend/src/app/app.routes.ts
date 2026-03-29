import { Routes } from '@angular/router';
import { LeaderboardComponent } from './pages/leaderboard/leaderboard.component';
import { ChannelDetailComponent } from './pages/channel-detail/channel-detail.component';

export const routes: Routes = [
  { path: '', component: LeaderboardComponent },
  { path: 'channel/:channelId', component: ChannelDetailComponent },
  { path: '**', redirectTo: '' },
];
