import React from 'react';
import { Cpu, Terminal, Database, Radio, Layers } from 'lucide-react';

export const ArchitectureSection: React.FC = () => {
  const nodes = [
    {
      title: "Jetpack Compose",
      type: "01. UI LAYER",
      desc: "Declarative UI with zero recomposition lag. Custom Modifier.liquidGlass() and dynamic palette extraction.",
      color: "text-purple-400",
      border: "border-purple-500/30",
      icon: Layers
    },
    {
      title: "Kyant0/Backdrop",
      type: "02. BLUR ENGINE",
      desc: "Source-included hardware backdrop capture via rememberLayerBackdrop() that samples raw framebuffers.",
      color: "text-cyan-400",
      border: "border-cyan-500/30",
      icon: Cpu
    },
    {
      title: "Media3 ExoPlayer",
      type: "03. AUDIO SERVICE",
      desc: "Persistent background service in MusicService.kt with notification media sessions and buffer management.",
      color: "text-pink-400",
      border: "border-pink-500/30",
      icon: Radio
    },
    {
      title: "Room & InnerTube",
      type: "04. DATA PIPELINE",
      desc: "Isolated InnerTube module for YouTube Music API querying without Google Play Services dependency.",
      color: "text-emerald-400",
      border: "border-emerald-500/30",
      icon: Database
    }
  ];

  return (
    <section id="architecture" className="py-24 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto relative z-10">
      
      {/* Header */}
      <div className="text-center max-w-3xl mx-auto mb-16 space-y-4">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full liquid-glass-pill text-xs font-mono font-semibold tracking-wider text-purple-300 uppercase border border-purple-500/20">
          <Terminal className="w-3.5 h-3.5 text-purple-400" />
          <span>System Blueprint</span>
        </div>
        <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white">
          Engineered for Clean <br />
          <span className="accent-gradient">Native Android Power.</span>
        </h2>
        <p className="text-slate-400 text-sm sm:text-base leading-relaxed">
          Clean MVVM separation, unidirectional data flow, and decoupled modules ensure instant launch times and smooth 120Hz scrolling on Android.
        </p>
      </div>

      {/* 4 Node Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {nodes.map((node, i) => {
          const Icon = node.icon;
          return (
            <div
              key={i}
              className={`liquid-glass liquid-glass-hover rounded-2xl p-6 border ${node.border} flex flex-col justify-between space-y-4`}
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className={`text-[11px] font-mono font-bold ${node.color}`}>{node.type}</span>
                  <Icon className={`w-4 h-4 ${node.color}`} />
                </div>
                <h4 className="text-lg font-bold text-white">{node.title}</h4>
                <p className="text-xs text-slate-300 leading-relaxed">{node.desc}</p>
              </div>

              <div className="pt-3 border-t border-white/10 text-[10px] font-mono text-slate-400">
                Pulse Wave Modular Core
              </div>
            </div>
          );
        })}
      </div>

    </section>
  );
};
