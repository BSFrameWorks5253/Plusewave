import React from 'react';
import { Tablet, Car, Smartphone, Laptop, Maximize2 } from 'lucide-react';

export const AdaptiveSection: React.FC = () => {
  return (
    <section id="screens" className="py-24 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto relative z-10">
      
      {/* Header */}
      <div className="text-center max-w-3xl mx-auto mb-16 space-y-4">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full liquid-glass-pill text-xs font-mono font-semibold tracking-wider text-emerald-300 uppercase border border-emerald-500/20">
          <Maximize2 className="w-3.5 h-3.5 text-emerald-400" />
          <span>Universal Display Engine</span>
        </div>
        <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white">
          Crafted for Tablets, Foldables <br />
          <span className="accent-gradient">& In-Car Cockpits.</span>
        </h2>
        <p className="text-slate-400 text-sm sm:text-base leading-relaxed">
          Whether you are listening on a phone, unfolding a foldable screen, docked to a tablet, or cruising with Android Auto — Pulse Wave's layout dynamically recalibrates.
        </p>
      </div>

      {/* Main Tablet Mockup Card */}
      <div className="liquid-glass rounded-3xl p-6 sm:p-8 border border-white/15 shadow-2xl space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs border-b border-white/10 pb-4">
          <div className="flex items-center gap-2">
            <Tablet className="w-4 h-4 text-cyan-400" />
            <span className="font-bold text-white text-sm">Dual-Pane Canonical Tablet Layout</span>
          </div>
          <span className="text-cyan-400 font-mono text-xs">10.5" to 14.6" Adaptive Breakpoint</span>
        </div>

        {/* Real Screenshot */}
        <div className="rounded-2xl overflow-hidden border border-white/10 shadow-inner">
          <img
            src="./assets/screen_tablet.png"
            alt="Pulse Wave Tablet Dual-Pane Screen"
            className="w-full object-cover max-h-[500px]"
          />
        </div>

        {/* 3 Pillars */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5 pt-2 text-xs">
          <div className="liquid-glass rounded-2xl p-4 border border-white/5 space-y-1.5">
            <div className="flex items-center gap-2 text-white font-bold text-sm">
              <Car className="w-4 h-4 text-yellow-400" />
              <span>Android Auto Certified</span>
            </div>
            <p className="text-slate-400 leading-relaxed">
              Tactile big-button controls and steering wheel multimedia keys support for safe driving.
            </p>
          </div>

          <div className="liquid-glass rounded-2xl p-4 border border-white/5 space-y-1.5">
            <div className="flex items-center gap-2 text-white font-bold text-sm">
              <Laptop className="w-4 h-4 text-cyan-400" />
              <span>Side-by-Side Dual Pane</span>
            </div>
            <p className="text-slate-400 leading-relaxed">
              Full-size album artwork and synced karaoke lyrics simultaneously visible without scrolling.
            </p>
          </div>

          <div className="liquid-glass rounded-2xl p-4 border border-white/5 space-y-1.5">
            <div className="flex items-center gap-2 text-white font-bold text-sm">
              <Smartphone className="w-4 h-4 text-purple-400" />
              <span>Zero-Flicker Rotation</span>
            </div>
            <p className="text-slate-400 leading-relaxed">
              Orientation changes maintain audio buffers and player state with no composition teardown.
            </p>
          </div>
        </div>
      </div>

    </section>
  );
};
