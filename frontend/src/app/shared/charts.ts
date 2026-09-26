import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild,
} from "@angular/core";
import { Chart, registerables } from "chart.js";
Chart.register(...registerables);
@Component({
  selector: "cq-chart",
  standalone: true,
  template: `<div class="chart-box">
    <canvas #canvas role="img" [attr.aria-label]="label">{{ label }}</canvas>
  </div>`,
})
export class Charts implements AfterViewInit, OnChanges, OnDestroy {
  @Input() labels: string[] = [];
  @Input() values: number[] = [];
  @Input() label = "Spese comuni per categoria";
  @Input() color = "#3d59c9";
  @ViewChild("canvas") canvas!: ElementRef<HTMLCanvasElement>;
  private chart?: Chart;
  ngAfterViewInit() {
    this.draw();
  }
  ngOnChanges() {
    if (this.canvas) this.draw();
  }
  ngOnDestroy() {
    this.chart?.destroy();
  }
  private draw() {
    this.chart?.destroy();
    this.chart = new Chart(this.canvas.nativeElement, {
      type: "bar",
      data: {
        labels: this.labels,
        datasets: [
          { data: this.values, backgroundColor: this.color, borderRadius: 6 },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (item) =>
                new Intl.NumberFormat("it-IT", {
                  style: "currency",
                  currency: "EUR",
                }).format(Number(item.raw)),
            },
          },
        },
        scales: {
          y: {
            beginAtZero: true,
            ticks: { callback: (value) => value + " €" },
          },
          x: { grid: { display: false } },
        },
      },
    });
  }
}
