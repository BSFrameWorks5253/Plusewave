import React from 'react';
import { Music, ShieldCheck, Gauge, Code2 } from 'lucide-react';

export const Metrics: React.FC = () => {
  const metrics = [
    {
      value: "100M+",
      label: "YouTube Music Tracks",
      sub: "Full streaming catalog",
      icon: Music,
      color: "text-purple-400"
    },
    {
      value: "0 KB",
      label: "Zero Telemetry & Trackers",
      sub: "100% on-device private",
      icon: ShieldCheck,
      color: "text-cyan-400"
    },
    {
      value: "120 Hz",
      label: "Liquid Glass Motion",
      sub: "Fluid Compose physics",
      icon: Gauge,
      color: "text-pink-400"
    },
    {
      value: "100%",
      label: "Free & Open-Source",
      sub: "GPL-3.0 Community Core",
      icon: Code2,
      color: "text-emerald-400"
    }
  ];

  return (
    <section className="border-y border-white/5 py-12 px-4 bg-slate-950/40 relative z-10">
      <div className="max-w-6xl mx-auto grid grid-cols-2 md:grid-cols-4 gap-8 text-center">
        {metrics.map((m, idx) => {
          const Icon = m.icon;
          return (
            <div key={idx} className="space-y-2">
              <div className="flex items-center justify-center gap-2">
                <Icon className={`w-5 h-5 ${m.color}`} />
                <p className="text-3xl sm:text-4xl font-extrabold text-white">{m.value}</p>
              </div>
              <p className="text-sm font-semibold text-slate-200">{m.label}</p>
              <p className="text-xs text-slate-400">{m.sub}</p>
            </div>
          );
        })}
      </div>
    </section>
  );
};
