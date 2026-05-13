import { Routes } from '@angular/router';
import { ContactFormComponent } from './contact-form/contact-form.component';

export const routes: Routes = [
  { path: '', component: ContactFormComponent },
  { path: '**', redirectTo: '' },
];
