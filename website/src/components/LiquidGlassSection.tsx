import React from 'react';
import { Layers, Sparkles, Sliders, Shield } from 'lucide-react';

export const LiquidGlassSection: React.FC = () => {
  return (
    <section id="about" className="py-24 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto relative z-10">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
        
        {/* Left: Narrative */}
        <div className="lg:col-span-5 space-y-6 text-left">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full liquid-glass-pill text-xs font-mono font-semibold tracking-wider text-purple-300 uppercase border border-purple-500/20">
            <Sparkles className="w-3.5 h-3.5 text-purple-400" />
            <span>Proprietary UI / UX Engine</span>
          </div>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight">
            Real Backdrop Blur. <br />
            <span className="accent-gradient">Not flat rectangles.</span>
          </h2>

          <p className="text-slate-300 text-sm sm:text-base leading-relaxed">
            Standard music players draw flat opaque rectangles that cover your album artwork. Pulse Wave features a custom port of <code className="text-cyan-300 font-mono">Kyant0/backdrop</code> in Jetpack Compose.
          </p>

          <p className="text-slate-400 text-sm leading-relaxed">
            Our glass captures actual hardware framebuffers beneath every component, refracting colors, adding iOS-style rubber-band overscroll, and applying specular edge highlights in real time.
          </p>

          {/* Key Spec Pillars */}
          <div className="space-y-4 pt-2">
            <div className="flex items-start gap-3.5">
              <div className="w-7 h-7 rounded-xl bg-cyan-500/10 border border-cyan-500/30 text-cyan-400 flex items-center justify-center shrink-0 mt-0.5">
                <Layers className="w-4 h-4" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-white">Dual-Pass Backdrop Sampling</h4>
                <p className="text-xs text-slate-400">Samples raw GPU framebuffer pixels and refracts through a custom displacement shader.</p>
              </div>
            </div>

            <div className="flex items-start gap-3.5">
              <div className="w-7 h-7 rounded-xl bg-purple-500/10 border border-purple-500/30 text-purple-400 flex items-center justify-center shrink-0 mt-0.5">
                <Sliders className="w-4 h-4" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-white">Adaptive Material You Extraction</h4>
                <p className="text-xs text-slate-400">Pulls harmonic color palettes from album art to tint ambient glows dynamically.</p>
              </div>
            </div>
          </div>
        </div>

        {/* Right: Real App Screenshot Card in Glass Frame */}
        <div className="lg:col-span-7">
          <div className="liquid-glass rounded-3xl p-4 sm:p-6 border border-white/15 relative overflow-hidden shadow-2xl">
            <div className="flex items-center justify-between pb-4 border-b border-white/10 text-xs">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-cyan-400 animate-pulse"></span>
                <span className="font-mono text-cyan-300 font-semibold">Pulse Wave Liquid Glass Player</span>
              </div>
              <span className="text-slate-400 font-mono">Modifier.liquidGlass()</span>
            </div>

            <div className="relative rounded-2xl overflow-hidden mt-4 border border-white/10 max-h-[480px]">
              <img
                src="./assets/screen_player.png"
                alt="Pulse Wave Liquid Glass Player"
                className="w-full object-cover"
              />
              
              {/* Floating Frosted Overlay Callout */}
              <div className="absolute bottom-4 left-4 right-4 liquid-glass rounded-2xl p-4 border border-white/20 backdrop-blur-2xl">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="text-xs font-bold text-white uppercase tracking-wider">Dynamic Specular Rim Highlight</h4>
                    <p className="text-[11px] text-slate-300">Frosted nav pucks and player controls reflect underlying artwork hues with zero frame drops.</p>
                  </div>
                  <span className="px-2.5 py-1 rounded-full bg-cyan-500/20 text-cyan-300 font-mono text-[10px] font-bold border border-cyan-500/40">
                    120 FPS
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

      </div>
    </section>
  );
};
