import { Routes } from '@angular/router';
import { LeaderboardComponent } from './pages/leaderboard/leaderboard.component';
import { ChannelDetailComponent } from './pages/channel-detail/channel-detail.component';
import { AdminComponent } from './pages/admin/admin.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { VerifyEmailComponent } from './pages/verify-email/verify-email.component';
import { ForgotPasswordComponent } from './pages/forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './pages/reset-password/reset-password.component';
import { FaqComponent } from './pages/faq/faq.component';
import { ContactComponent } from './pages/contact/contact.component';
import { PrivacyPolicyComponent } from './pages/privacy-policy/privacy-policy.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: LeaderboardComponent },
  { path: 'channel/:channelId', component: ChannelDetailComponent },
  { path: 'admin', component: AdminComponent, canActivate: [authGuard] },
  { path: 'faq', component: FaqComponent },
  { path: 'contact', component: ContactComponent, canActivate: [authGuard] },
  { path: 'privacy', component: PrivacyPolicyComponent },
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: RegisterComponent },
  { path: 'verify-email', component: VerifyEmailComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: '**', redirectTo: '' },
];
