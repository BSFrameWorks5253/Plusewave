import React, { useState } from 'react';
import { Mic2, ChevronRight, CheckCircle2, Music2 } from 'lucide-react';

const KARAOKE_LINES = [
  "Floating above the neon skylines of the cyber city...",
  "Liquid glass reflects the sound resonating within.",
  "Every single pulse harmonizes with the rhythmic beat...",
  "100% offline, zero trackers, pure melodic freedom."
];

export const LyricsSection: React.FC = () => {
  const [activeLine, setActiveLine] = useState(1);

  const nextLine = () => {
    setActiveLine((prev) => (prev + 1) % KARAOKE_LINES.length);
  };

  return (
    <section id="lyrics" className="py-24 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto relative z-10">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
        
        {/* Left: Real App Screenshot in Phone Frame */}
        <div className="lg:col-span-6 flex justify-center order-2 lg:order-1">
          <div className="w-[300px] sm:w-[320px] rounded-[44px] overflow-hidden shadow-2xl border-4 border-[#1f2333] liquid-glass p-2 bg-[#090b14]">
            <div className="w-full h-full rounded-[36px] overflow-hidden">
              <img
                src="./assets/screen_lyrics.png"
                alt="Synced Karaoke Lyrics Screen"
                className="w-full object-cover"
              />
            </div>
          </div>
        </div>

        {/* Right: Narrative & Live Interactive Lyrics Reader */}
        <div className="lg:col-span-6 space-y-6 text-left order-1 lg:order-2">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full liquid-glass-pill text-xs font-mono font-semibold tracking-wider text-pink-400 uppercase border border-pink-500/20">
            <Mic2 className="w-3.5 h-3.5 text-pink-400" />
            <span>BetterLyrics & LRCLIB Core</span>
          </div>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight">
            Karaoke Lyrics that <br />
            <span className="accent-gradient">sing in real time.</span>
          </h2>

          <p className="text-slate-300 text-sm sm:text-base leading-relaxed">
            Follow your favorite songs with word-by-word synced karaoke highlights. Pulse Wave aggregates multi-source lyrics from LRCLIB, Musixmatch, Kugou, and SimpMusic with zero lag.
          </p>

          {/* Live Interactive Karaoke Box */}
          <div className="liquid-glass rounded-2xl p-6 border border-white/15 space-y-4 shadow-xl">
            <div className="flex items-center justify-between text-xs font-mono text-slate-400 border-b border-white/10 pb-3">
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-pink-400 animate-pulse"></span>
                <span className="text-pink-300 font-bold">KARAOKE STREAM ENGINE</span>
              </div>
              <span>Click "Next Line" to test</span>
            </div>
            
            <div className="space-y-3 py-3 min-h-[160px] flex flex-col justify-center">
              {KARAOKE_LINES.map((text, idx) => {
                const isActive = idx === activeLine;
                return (
                  <p
                    key={idx}
                    className={`transition-all duration-300 ${
                      isActive
                        ? 'text-lg sm:text-xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 via-purple-400 to-pink-400 scale-105 origin-left'
                        : 'text-sm text-slate-400 opacity-40'
                    }`}
                  >
                    "{text}"
                  </p>
                );
              })}
            </div>

            <div className="flex items-center justify-between pt-2 border-t border-white/10">
              <button
                onClick={nextLine}
                className="btn-primary-gradient px-4 py-2 rounded-xl text-xs font-bold text-white flex items-center gap-1.5 cursor-pointer shadow-md"
              >
                <span>Next Lyric Line</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
              <div className="flex items-center gap-1.5 text-xs text-slate-400">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                <span>Instant Offline Cache</span>
              </div>
            </div>
          </div>

        </div>

      </div>
    </section>
  );
};
