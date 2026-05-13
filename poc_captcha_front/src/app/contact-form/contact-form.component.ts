import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { CommonModule } from '@angular/common';
import { FriendlyCaptchaSDK, WidgetHandle } from '@friendlycaptcha/sdk';
import { ContactService } from '../services/contact.service';
import { environment } from '../../environments/environment';

type FormStatus = 'idle' | 'loading' | 'success' | 'error' | 'captcha-missing';

@Component({
  selector: 'app-contact-form',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule],
  templateUrl: './contact-form.component.html',
  styleUrl: './contact-form.component.css',
})
export class ContactFormComponent implements AfterViewInit, OnDestroy {
  @ViewChild('captchaContainer') captchaContainer!: ElementRef<HTMLElement>;

  form: FormGroup;
  status: FormStatus = 'idle';
  errorMessage = '';

  private captchaToken: string | null = null;
  private widget: WidgetHandle | null = null;

  constructor(
    private fb: FormBuilder,
    private contactService: ContactService,
  ) {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      message: ['', [Validators.required, Validators.minLength(10)]],
    });
  }

  ngAfterViewInit(): void {
    const sdk = new FriendlyCaptchaSDK();
    this.widget = sdk.createWidget({
      element: this.captchaContainer.nativeElement,
      sitekey: environment.friendlyCaptchaSiteKey,
      startMode: 'auto',
    });

    this.captchaContainer.nativeElement.addEventListener(
      'frc:widget.complete',
      (event: Event) => {
        this.captchaToken = (
          event as CustomEvent<{ response: string }>
        ).detail.response;
        if (this.status === 'captcha-missing') this.status = 'idle';
      },
    );

    this.captchaContainer.nativeElement.addEventListener(
      'frc:widget.error',
      () => {
        this.captchaToken = null;
      },
    );

    this.captchaContainer.nativeElement.addEventListener(
      'frc:widget.expire',
      () => {
        this.captchaToken = null;
      },
    );
  }

  ngOnDestroy(): void {
    this.widget?.destroy();
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (!this.captchaToken) {
      this.status = 'captcha-missing';
      return;
    }

    this.status = 'loading';

    this.contactService
      .send({
        name: this.form.value.name,
        email: this.form.value.email,
        message: this.form.value.message,
        captchaResponse: this.captchaToken,
      })
      .subscribe({
        next: () => {
          this.status = 'success';
          this.form.reset();
          this.captchaToken = null;
          this.widget?.reset();
        },
        error: (err) => {
          this.status = 'error';
          this.captchaToken = null;
          this.widget?.reset();
          this.errorMessage =
            err.status === 400
              ? 'La validation du captcha a échoué. Veuillez réessayer.'
              : 'Une erreur technique est survenue. Veuillez réessayer plus tard.';
        },
      });
  }

  isFieldInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!(control && control.invalid && control.touched);
  }
}
