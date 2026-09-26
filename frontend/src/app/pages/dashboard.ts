import { Component, computed, inject, signal } from "@angular/core";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { FormsModule } from "@angular/forms";
import { Store } from "../core/store";
import { Charts } from "../shared/charts";
import { Notices } from "./notices";
import { today, plusDays } from "../core/dates";
@Component({
  selector: "cq-dashboard",
  standalone: true,
  imports: [RouterLink, FormsModule, Charts, Notices],
  template: `@if (personal) {
      <div class="page-title">
        <div>
          <h1>Ciao, {{ s.user()?.name }}.</h1>
          <p>I tuoi impegni e il tuo saldo.</p>
        </div>
        <span class="pill">Riepilogo personale</span>
      </div>
      <div class="stats">
        <section class="stat">
          <span>Il tuo saldo</span
          ><strong>{{ s.euros(s.data()!.myBalance) }}</strong
          ><small>{{
            s.data()!.myBalance > 0
              ? "Da ricevere"
              : s.data()!.myBalance < 0
                ? "Da restituire"
                : "Conti in pari"
          }}</small>
        </section>
        <section class="stat">
          <span>I tuoi turni</span><strong>{{ myTurns().length }}</strong
          ><small>Da completare</small>
        </section>
        <section class="stat">
          <span>Lista condivisa</span><strong>{{ items().length }}</strong
          ><small>Prodotti da comprare</small>
        </section>
      </div>
      <div class="grid two">
        <section class="panel">
          <div class="section-title">
            <h2>Le tue mansioni</h2>
            <a routerLink="/mansioni">Gestisci →</a>
          </div>
          @for (t of myTurns(); track t.id) {
            <div class="row">
              <span class="emoji">{{ task(t.taskId)?.emoji }}</span>
              <div class="grow">
                <strong>{{ task(t.taskId)?.title }}</strong
                ><small>{{ s.date(t.due) }}</small>
              </div>
              <button
                [disabled]="s.busy()"
                (click)="
                  s.send('tasks/complete', { id: t.id }, 'Turno completato.')
                "
              >
                ✓ Fatto
              </button>
            </div>
          } @empty {
            <p class="empty">Non hai mansioni da completare.</p>
          }
        </section>
        <section class="panel">
          <div class="section-title">
            <h2>Sistemiamo i conti</h2>
            <a routerLink="/spese">Apri spese →</a>
          </div>
          @for (r of s.data()!.settlements; track $index) {
            <div class="row">
              <div class="grow">
                <strong
                  >{{ s.name(r.fromUser) }} → {{ s.name(r.toUser) }}</strong
                ><small>Suggerimento di saldo</small>
              </div>
              <strong>{{ s.euros(r.cents) }}</strong>
            </div>
          } @empty {
            <p class="empty">Nessun rimborso suggerito per te.</p>
          }
        </section>
      </div>
    } @else {
      <section class="hero">
        <div>
          <span class="kicker">BACHECA CONDIVISA</span>
          <h1>{{ s.house()?.emoji }} {{ s.house()?.name }}</h1>
          <p>Come va la casa e cosa c’è da fare.</p>
        </div>
        @if (s.house()?.photo) {
          <img
            class="house-cover"
            [src]="s.house()?.photo"
            alt="Foto della casa"
          />
        } @else {
          <div class="avatar-group">
            @for (m of s.activeMembers(); track m.id) {
              <span class="avatar" [attr.title]="m.name">
                @if (m.photo) {
                  <img [src]="m.photo" [alt]="m.name" />
                } @else {
                  {{ m.name.slice(0, 2).toUpperCase() }}
                }
              </span>
            }
          </div>
        }
      </section>
      <div class="stats">
        <section class="stat">
          <span>Mansioni della settimana</span
          ><strong>{{ progress().done }} / {{ progress().total }}</strong
          ><small>Completate insieme</small
          ><progress
            [value]="progress().done"
            [max]="progress().total || 1"
            aria-label="Mansioni completate"
          ></progress>
        </section>
        <section class="stat">
          <span>Spese comuni del mese</span
          ><strong>{{ s.euros(total()) }}</strong
          ><small>Condivise con tutta la casa</small>
        </section>
        <section class="stat">
          <span>Da comprare</span><strong>{{ items().length }}</strong
          ><small>Prodotti nella lista condivisa</small>
        </section>
      </div>
      <div class="grid two">
        <div class="stack">
          <section class="panel">
            <div class="section-title">
              <h2>Prossime mansioni</h2>
              <a routerLink="/mansioni">Gestisci →</a>
            </div>
            @for (t of upcoming().slice(0, 5); track t.id) {
              <div class="row">
                <span class="emoji">{{ task(t.taskId)?.emoji }}</span>
                <div class="grow">
                  <strong>{{ task(t.taskId)?.title }}</strong
                  ><small>{{ s.name(t.userId) }} · {{ s.date(t.due) }}</small>
                </div>
                @if (t.due < now) {
                  <span class="pill overdue">In ritardo</span>
                }
              </div>
            } @empty {
              <p class="empty">
                Tutto in ordine: non ci sono mansioni in programma.
              </p>
            }
          </section>
          <section class="panel">
            <div class="section-title">
              <h2>Lista della spesa</h2>
              <a routerLink="/lista">Apri lista →</a>
            </div>
            @for (i of items().slice(0, 5); track i.id) {
              <div class="row">
                <strong class="grow">{{ i.name }}</strong
                ><span>{{ i.quantity }} {{ i.unit }}</span>
              </div>
            } @empty {
              <p class="empty">La lista è vuota.</p>
            }
          </section>
        </div>
        <div class="stack">
          <cq-notices />
          <section class="panel">
            <div class="section-title">
              <h2>Dove spendiamo insieme</h2>
              <input
                type="month"
                aria-label="Mese delle spese"
                [ngModel]="month()"
                (ngModelChange)="month.set($event)"
              />
            </div>
            @if (total() > 0) {
              <cq-chart
                [labels]="categories"
                [values]="chartValues()"
                [color]="s.user()!.color"
              />
            } @else {
              <p class="empty">Nessuna spesa comune nel mese selezionato.</p>
            }
          </section>
        </div>
      </div>
      <section class="panel top-gap">
        <div class="section-title">
          <h2>Ultime spese di tutta la casa</h2>
          <span class="pill">Visibili a tutti</span>
        </div>
        @for (e of common().slice(0, 5); track e.id) {
          <div class="row">
            <div class="grow">
              <strong>{{ e.title }}</strong
              ><small
                >{{ s.name(e.paidBy) }} ha pagato · {{ s.date(e.date) }}</small
              >
            </div>
            <strong>{{ s.euros(e.cents) }}</strong>
          </div>
        } @empty {
          <p class="empty">Le spese condivise con tutti appariranno qui.</p>
        }
      </section>
    }`,
})
export class Dashboard {
  s = inject(Store);
  personal = !!inject(ActivatedRoute).snapshot.data["personal"];
  now = today();
  month = signal(this.now.slice(0, 7));
  categories = ["Spesa", "Bollette", "Casa", "Altro"];
  task(id: number) {
    return this.s.data()?.tasks.find((t) => t.id === id);
  }
  upcoming = computed(() =>
    this.s
      .data()!
      .turns.filter(
        (t) =>
          !t.done &&
          this.s.data()!.tasks.some((x) => x.id === t.taskId && x.active),
      )
      .sort((a, b) => a.due.localeCompare(b.due)),
  );
  myTurns = computed(() =>
    this.upcoming().filter((t) => t.userId === this.s.user()?.id),
  );
  items = computed(() => this.s.data()!.items.filter((i) => !i.bought));
  common = computed(() =>
    this.s
      .data()!
      .expenses.filter((e) => e.shared && e.date.startsWith(this.month()))
      .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id),
  );
  total = computed(() => this.common().reduce((s, e) => s + e.cents, 0));
  chartValues = computed(() =>
    this.categories.map(
      (c) =>
        this.common()
          .filter((e) => e.category === c)
          .reduce((s, e) => s + e.cents, 0) / 100,
    ),
  );
  progress = computed(() => {
    const d = new Date(this.now + "T12:00:00Z");
    const start = plusDays(this.now, -((d.getUTCDay() + 6) % 7));
    const end = plusDays(start, 6);
    const turns = this.s
      .data()!
      .turns.filter(
        (t) =>
          t.due >= start &&
          t.due <= end &&
          (t.done || this.task(t.taskId)?.active),
      );
    return { done: turns.filter((t) => t.done).length, total: turns.length };
  });
}
