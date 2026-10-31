import React, { useState } from 'react';
import { HardDriveDownload, SlidersHorizontal, Radio, AudioLines, Flame } from 'lucide-react';

export const StreamingSection: React.FC = () => {
  const [eqPreset, setEqPreset] = useState<'bass' | 'vocal' | 'electronic' | 'flat'>('bass');

  const getPresetHeights = () => {
    switch (eqPreset) {
      case 'bass':
        return [85, 92, 78, 65, 52, 45, 40, 38, 42, 48, 55, 60, 65, 70, 75, 80];
      case 'vocal':
        return [30, 35, 42, 58, 75, 90, 95, 88, 72, 60, 50, 45, 40, 38, 35, 30];
      case 'electronic':
        return [90, 85, 70, 45, 35, 40, 60, 75, 85, 92, 80, 65, 50, 68, 82, 90];
      case 'flat':
      default:
        return [50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50];
    }
  };

  const heights = getPresetHeights();

  return (
    <section id="streaming" className="py-24 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto relative z-10">
      
      {/* Section Header */}
      <div className="text-center max-w-3xl mx-auto mb-16 space-y-4">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full liquid-glass-pill text-xs font-mono font-semibold tracking-wider text-cyan-300 uppercase border border-cyan-500/20">
          <Radio className="w-3.5 h-3.5 text-cyan-400" />
          <span>ExoPlayer & InnerTube Core</span>
        </div>
        <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white">
          Unlimited Streaming. <br />
          <span className="accent-gradient">High-Fidelity Audio.</span>
        </h2>
        <p className="text-slate-400 text-sm sm:text-base leading-relaxed">
          Engineered with an isolated Media3 ExoPlayer service that never pauses when switching apps, paired with direct InnerTube client streaming.
        </p>
      </div>

      {/* 3 Main Feature Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-12">
        
        {/* Card 1 */}
        <div className="liquid-glass liquid-glass-hover rounded-3xl p-8 flex flex-col justify-between">
          <div className="space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-red-500/10 border border-red-500/30 flex items-center justify-center text-red-400">
              <svg className="w-6 h-6 fill-current" viewBox="0 0 24 24">
                <path d="M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"/>
              </svg>
            </div>
            <h3 className="text-xl font-bold text-white">Full YT Music Catalog</h3>
            <p className="text-sm text-slate-300 leading-relaxed">
              Access over 100 million official tracks, community remixes, and live DJ sets without advertisements or forced subscription walls.
            </p>
          </div>
          <div className="pt-6 border-t border-white/10 flex items-center justify-between text-xs text-slate-400 mt-6">
            <span>InnerTube Engine</span>
            <span className="text-emerald-400 font-mono font-semibold">100% Free</span>
          </div>
        </div>

        {/* Card 2 */}
        <div className="liquid-glass liquid-glass-hover rounded-3xl p-8 flex flex-col justify-between">
          <div className="space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-purple-500/10 border border-purple-500/30 flex items-center justify-center text-purple-400">
              <HardDriveDownload className="w-6 h-6" />
            </div>
            <h3 className="text-xl font-bold text-white">Offline Smart Caching</h3>
            <p className="text-sm text-slate-300 leading-relaxed">
              Save complete playlists and albums into encrypted local Room DB storage. Enjoy seamless playback on flights or low-coverage subways.
            </p>
          </div>
          <div className="pt-6 border-t border-white/10 flex items-center justify-between text-xs text-slate-400 mt-6">
            <span>Room Database</span>
            <span className="text-purple-400 font-mono font-semibold">Lossless FLAC</span>
          </div>
        </div>

        {/* Card 3 */}
        <div className="liquid-glass liquid-glass-hover rounded-3xl p-8 flex flex-col justify-between">
          <div className="space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <SlidersHorizontal className="w-6 h-6" />
            </div>
            <h3 className="text-xl font-bold text-white">Hardware Equalizer</h3>
            <p className="text-sm text-slate-300 leading-relaxed">
              Fine-tune acoustics with hardware DSP bass boost, vocal clarity enhancers, virtualizer stereo widener, and volume normalization.
            </p>
          </div>
          <div className="pt-6 border-t border-white/10 flex items-center justify-between text-xs text-slate-400 mt-6">
            <span>AudioTrack DSP</span>
            <span className="text-cyan-400 font-mono font-semibold">32-Bit Float</span>
          </div>
        </div>

      </div>

      {/* Interactive Live Equalizer Lab */}
      <div className="liquid-glass rounded-3xl p-6 sm:p-8 border border-white/15 shadow-2xl">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
          <div>
            <div className="flex items-center gap-2">
              <AudioLines className="w-5 h-5 text-cyan-400" />
              <h4 className="text-lg font-bold text-white">Live DSP Equalizer Spectrum</h4>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">Test real-time frequency curves across the 16 acoustic channels</p>
          </div>

          <div className="flex items-center gap-2">
            {(['bass', 'vocal', 'electronic', 'flat'] as const).map((preset) => (
              <button
                key={preset}
                onClick={() => setEqPreset(preset)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold capitalize transition-all cursor-pointer ${
                  eqPreset === preset
                    ? 'btn-primary-gradient text-white shadow-md'
                    : 'liquid-glass-pill text-slate-300 hover:text-white'
                }`}
              >
                {preset === 'bass' ? 'Bass Boost' : preset === 'vocal' ? 'Vocal Crisp' : preset === 'electronic' ? 'Electronic' : 'Studio Flat'}
              </button>
            ))}
          </div>
        </div>

        {/* Spectrum Canvas Display */}
        <div className="h-32 flex items-end justify-between gap-1.5 px-4 py-3 bg-[#090b14] rounded-2xl border border-white/10">
          {heights.map((h, i) => (
            <div
              key={i}
              className="flex-1 bg-gradient-to-t from-cyan-500 via-purple-500 to-pink-500 rounded-t-md transition-all duration-300"
              style={{ height: `${h}%` }}
            />
          ))}
        </div>
      </div>

    </section>
  );
};
