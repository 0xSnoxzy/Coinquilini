import { Component, inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Store } from "../core/store";
@Component({
  selector: "cq-auth",
  standalone: true,
  imports: [FormsModule],
  template: `<div class="auth-wrap">
    <section class="auth-intro">
      <div class="brand"><span class="logo">⌂</span>Coinquilini</div>
      <h1>La vostra casa.<br />Un po’ più organizzata.</h1>
      <p>Spese, lista della spesa e mansioni, nello stesso posto.</p>
      <div class="auth-emoji">🧊 🧽 🛒</div>
    </section>
    <section class="auth-card">
      <h2>{{ register ? "Crea il tuo account" : "Bentornato a casa" }}</h2>
      <p class="muted">
        {{
          register
            ? "Poi potrai creare una casa o usare un invito."
            : "Accedi al tuo spazio condiviso."
        }}
      </p>
      <form #form="ngForm" (ngSubmit)="submit()">
        @if (register) {
          <label
            >Nome<input
              name="name"
              [(ngModel)]="name"
              required
              maxlength="50"
              autocomplete="name"
          /></label>
        }
        <label
          >Email<input
            type="email"
            name="email"
            [(ngModel)]="email"
            required
            email
            autocomplete="username" /></label
        ><label
          >Password<input
            type="password"
            name="password"
            [(ngModel)]="password"
            required
            minlength="8"
            maxlength="72"
            [attr.autocomplete]="
              register ? 'new-password' : 'current-password'
            " /></label
        ><button class="primary full" [disabled]="form.invalid || s.busy()">
          {{ s.busy() ? "Attendi…" : register ? "Registrati" : "Accedi" }}
        </button>
      </form>
      <button class="link" (click)="register = !register">
        {{
          register ? "Hai già un account? Accedi" : "Prima volta? Registrati"
        }}
      </button>
      @if (s.mode() === "demo") {
        <div class="demo-note">
          <strong>Esplora l’interfaccia</strong>
          <p>
            Puoi anche esplorare le pagine con dati di esempio.
          </p>
          <div class="actions">
            @for (person of ["Antonio", "David", "Elisa", "Giovanna", "Admin", "Nuovo"]; track person) {
              <button type="button" (click)="choose(person)">
                {{ person }}
              </button>
            }
          </div>
          <small
            >Antonio: responsabile (OWNER) · David, Elisa, Giovanna: coinquilini (MEMBER) · Admin: gestione · Nuovo:
            ingresso in casa. I dati si azzerano al ricaricamento.</small
          >
        </div>
      }
    </section>
  </div>`,
})
export class Auth {
  s = inject(Store);
  register = false;
  name = "";
  email = "";
  password = "";
  choose(person: string) {
    this.s.preview(person);
  }
  async submit() {
    await this.s.auth(this.register, this.name, this.email, this.password);
  }
}
