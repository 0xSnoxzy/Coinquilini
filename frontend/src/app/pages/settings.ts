import { Component, inject, OnDestroy } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Store } from "../core/store";
import { applyTheme } from "../core/theme";
import { Member } from "../core/models";
// Responsabilità suggerita: Antonio: profili, casa, inviti e appartenenze.
@Component({
  selector: "cq-settings",
  standalone: true,
  imports: [FormsModule],
  template: `<div class="page-title">
      <div>
        <h1>Una casa che vi somiglia.</h1>
        <p>Il vostro spazio e le tue preferenze.</p>
      </div>
    </div>
    <div class="grid two">
      <div class="stack">
        <section class="panel">
          <h2>Il tuo profilo</h2>
          <div class="profile-preview">
            <span class="avatar large">
              @if (profile.photo) {
                <img [src]="profile.photo" alt="La tua foto" />
              } @else {
                {{ profile.name.slice(0, 2) }}
              }</span
            ><strong>{{ profile.name }}</strong>
          </div>
          <form #pf="ngForm" (ngSubmit)="saveProfile()">
            <label
              >Nome visualizzato<input
                name="name"
                [(ngModel)]="profile.name"
                required
                maxlength="50" /></label
            ><label
              >Foto profilo<input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                (change)="photo($event, 'profile')"
            /></label>
            @if (profile.photo) {
              <button type="button" class="link" (click)="profile.photo = ''">
                Rimuovi foto
              </button>
            }
            <label
              >Il tuo colore<input
                type="color"
                name="color"
                [(ngModel)]="profile.color"
                (ngModelChange)="preview($event)"
            /></label>
            <div class="actions">
              @for (p of presets; track p.color) {
                <button
                  type="button"
                  class="swatch"
                  [style.--swatch]="p.color"
                  (click)="profile.color = p.color; preview(p.color)"
                >
                  <span></span>{{ p.name }}
                </button>
              }
            </div>
            <button class="primary" [disabled]="pf.invalid || s.busy()">
              Salva profilo e colore
            </button>
          </form>
        </section>
        <section class="panel">
          <h2>La vostra casa</h2>
          <form #hf="ngForm" (ngSubmit)="s.send('houses/save', house)">
            <label
              >Nome<input
                name="houseName"
                [(ngModel)]="house.name"
                [disabled]="!s.owner()"
                required
                maxlength="50" /></label
            ><label
              >Emoji<input
                name="emoji"
                [(ngModel)]="house.emoji"
                [disabled]="!s.owner()"
                maxlength="12"
                required
                list="house-emojis" /><datalist id="house-emojis">
                @for (e of emojis; track e) {
                  <option [value]="e"></option>
                }</datalist
            ></label>
            @if (house.photo) {
              <img
                class="settings-cover"
                [src]="house.photo"
                alt="Foto della casa"
              />
            }
            @if (s.owner()) {
              <label
                >Foto della casa<input
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  (change)="photo($event, 'house')"
              /></label>
              @if (house.photo) {
                <button type="button" class="link" (click)="house.photo = ''">
                  Rimuovi foto casa
                </button>
              }
              <button class="primary" [disabled]="hf.invalid || s.busy()">
                Salva casa
              </button>
            } @else {
              <small
                >Le impostazioni della casa sono gestite dal
                responsabile.</small
              >
            }
          </form>
        </section>
      </div>
      <div class="stack">
        <section class="panel">
          <h2>I coinquilini</h2>
          @for (m of s.members(); track m.id) {
            <div class="member-row">
              <span class="avatar">
                @if (m.photo) {
                  <img [src]="m.photo" [alt]="m.name" />
                } @else {
                  {{ m.name.slice(0, 2) }}
                }
              </span>
              <div class="grow">
                <strong>{{ m.name }}</strong
                ><small>{{
                  !m.memberActive
                    ? "Ex coinquilino"
                    : m.membershipRole === "OWNER"
                      ? "Responsabile"
                      : "Coinquilino"
                }}</small>
              </div>
              @if (s.owner() && m.memberActive && !s.mine(m.id)) {
                <details>
                  <summary aria-label="Gestisci coinquilino">Gestisci</summary>
                  <div class="stack small-gap">
                    <button (click)="transfer(m)">Nomina responsabile</button
                    ><button (click)="remove(m)">Rimuovi dalla casa</button>
                  </div>
                </details>
              }
            </div>
          }
        </section>
        @if (s.owner()) {
          <section class="panel">
            <h2>Invita un coinquilino</h2>
            <p>Condividi questo codice con chi deve entrare in casa.</p>
            <div class="invite-code">{{ s.house()?.inviteCode }}</div>
            <button (click)="rotate()" [disabled]="s.busy()">
              Genera un nuovo codice</button
            ><small>Il codice precedente smetterà di funzionare.</small>
          </section>
        }
        <section class="panel">
          <h2>Appartenenza alla casa</h2>
          <p class="muted">
            Prima di uscire, il tuo saldo deve essere in pari. Il responsabile
            deve trasferire il ruolo se rimangono altri membri.
          </p>
          <button (click)="leave()">Esci dalla casa</button>
        </section>
      </div>
    </div>`,
})
export class Settings implements OnDestroy {
  s = inject(Store);
  profile = { ...this.s.user()! };
  house = { ...this.s.house()! };
  presets = [
    { name: "Blu", color: "#3d59c9" },
    { name: "Violetto", color: "#7250bc" },
    { name: "Azzurro", color: "#16758e" },
  ];
  emojis = ["🧊", "🏡", "🪴", "🐈", "🌊", "🎮"];
  preview = applyTheme;
  async saveProfile() {
    await this.s.send("profile/save", this.profile);
  }
  async photo(event: Event, target: "profile" | "house") {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file)
      try {
        this[target].photo = await this.s.photo(file);
      } catch (e) {
        this.s.fail(e);
      }
  }
  ngOnDestroy() {
    applyTheme(this.s.user()?.color || "#3d59c9");
  }
  transfer(m: Member) {
    if (confirm("Assegnare a " + m.name + " il ruolo di responsabile?"))
      void this.s.send("houses/owner", { userId: m.id });
  }
  remove(m: Member) {
    if (
      confirm(
        "Rimuovere " + m.name + " dalla casa? Lo storico rimarrà disponibile.",
      )
    )
      void this.s.send("houses/remove", { userId: m.id });
  }
  rotate() {
    if (confirm("Generare un nuovo codice d’invito?"))
      void this.s.send("houses/invite", {});
  }
  leave() {
    if (confirm("Vuoi uscire dalla casa?"))
      void this.s.send("houses/leave", {});
  }
}
