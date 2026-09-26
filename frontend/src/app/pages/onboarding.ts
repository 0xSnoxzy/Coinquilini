import { Component, inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Store } from "../core/store";
@Component({
  selector: "cq-onboarding",
  standalone: true,
  imports: [FormsModule],
  template: `<div class="page-title">
      <div>
        <h1>Ciao, {{ s.user()?.name }}.</h1>
        <p>Crea il vostro spazio oppure entra con un codice d’invito.</p>
      </div>
    </div>
    <div class="grid two">
      <section class="panel">
        <h2>Una nuova casa</h2>
        <form
          #create="ngForm"
          (ngSubmit)="s.send('houses/create', { name, emoji })"
        >
          <label
            >Nome della casa<input
              name="name"
              [(ngModel)]="name"
              required
              maxlength="50"
              placeholder="Casa Prova" /></label
          ><label
            >Emoji<select name="emoji" [(ngModel)]="emoji">
              @for (e of emojis; track e) {
                <option>{{ e }}</option>
              }
            </select></label
          ><button class="primary" [disabled]="create.invalid || s.busy()">
            Crea casa
          </button>
        </form>
      </section>
      <section class="panel">
        <h2>Hai ricevuto un invito?</h2>
        <form #join="ngForm" (ngSubmit)="s.send('houses/join', { code })">
          <label
            >Codice d’invito<input
              name="code"
              [(ngModel)]="code"
              required
              maxlength="20"
              placeholder="PROVA26"
              class="code" /></label
          ><button class="primary" [disabled]="join.invalid || s.busy()">
            Entra nella casa
          </button>
        </form>
      </section>
    </div>`,
})
export class Onboarding {
  s = inject(Store);
  name = "";
  emoji = "🧊";
  code = "";
  emojis = ["🧊", "🏡", "🪴", "🐈", "🌊", "🎮"];
}
