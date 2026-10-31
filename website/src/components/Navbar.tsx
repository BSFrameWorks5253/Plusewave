import React from 'react';
import { Download, Volume2, VolumeX, Sparkles } from 'lucide-react';

interface NavbarProps {
  isSynthPlaying: boolean;
  onToggleSynth: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ isSynthPlaying, onToggleSynth }) => {
  return (
    <header className="fixed top-4 left-0 right-0 z-50 px-4 sm:px-6">
      <div className="max-w-6xl mx-auto liquid-glass rounded-full px-5 py-3 flex items-center justify-between border border-white/10 shadow-2xl">
        
        {/* Brand */}
        <a href="#top" className="flex items-center gap-3 group">
          <div className="w-9 h-9 rounded-xl p-1 bg-gradient-to-tr from-cyan-500 to-purple-600 flex items-center justify-center shadow-lg shadow-purple-500/20 group-hover:scale-105 transition-transform">
            <img src="./assets/logo.png" alt="Pulse Wave" className="w-full h-full object-contain" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="font-extrabold tracking-wide text-base text-white">PULSE WAVE</span>
              <span className="px-1.5 py-0.5 rounded-md bg-purple-500/20 text-purple-300 font-mono text-[9px] font-bold border border-purple-500/30">v6.1</span>
            </div>
            <span className="text-[10px] text-cyan-400 font-mono tracking-wider block -mt-0.5 font-semibold">LIQUID GLASS</span>
          </div>
        </a>

        {/* Desktop Links */}
        <nav className="hidden md:flex items-center gap-7 text-sm font-medium text-slate-300">
          <a href="#about" className="hover:text-cyan-400 transition-colors">Design</a>
          <a href="#streaming" className="hover:text-cyan-400 transition-colors">Streaming</a>
          <a href="#lyrics" className="hover:text-cyan-400 transition-colors">Karaoke Lyrics</a>
          <a href="#screens" className="hover:text-cyan-400 transition-colors">Adaptive UI</a>
          <a href="#privacy" className="hover:text-cyan-400 transition-colors">Privacy</a>
          <a href="#architecture" className="hover:text-cyan-400 transition-colors">Engine</a>
        </nav>

        {/* Action Controls */}
        <div className="flex items-center gap-3">
          <button
            onClick={onToggleSynth}
            className="liquid-glass-pill px-3 py-1.5 rounded-full flex items-center gap-2 text-xs font-medium text-slate-300 hover:text-cyan-300 transition-colors cursor-pointer"
            title="Toggle Procedural Web Audio Synth Preview"
          >
            {isSynthPlaying ? (
              <>
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                <Volume2 className="w-3.5 h-3.5 text-emerald-400" />
                <span className="hidden sm:inline font-mono text-[11px]">Synth: ON</span>
              </>
            ) : (
              <>
                <span className="w-2 h-2 rounded-full bg-slate-500"></span>
                <VolumeX className="w-3.5 h-3.5 text-slate-400" />
                <span className="hidden sm:inline font-mono text-[11px]">Audio Demo</span>
              </>
            )}
          </button>

          <a
            href="https://github.com/BSFrameworks5253/Plusewave/releases/latest"
            target="_blank"
            rel="noreferrer"
            className="btn-primary-gradient px-4 py-2 rounded-full text-xs font-bold text-white tracking-wide flex items-center gap-1.5 shadow-lg"
          >
            <span>Get APK</span>
            <Download className="w-3.5 h-3.5" />
          </a>
        </div>

      </div>
    </header>
  );
};
