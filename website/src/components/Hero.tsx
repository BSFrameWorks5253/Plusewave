import React, { useState, useEffect } from 'react';
import { Download, Heart, Play, Pause, SkipBack, SkipForward, Disc3, ShieldCheck, Sparkles, ExternalLink } from 'lucide-react';

interface HeroProps {
  onOpenUpi: () => void;
  isAudioPlaying: boolean;
  onToggleAudio: () => void;
}

const DEMO_SONGS = [
  {
    title: "Midnight Pulse Wave",
    artist: "BS.Frameworks • Synthwave",
    art: "./assets/screen_lyrics.png",
    format: "FLAC 1411 kbps"
  },
  {
    title: "Finding Her (Live Studio)",
    artist: "ExoPlayer Media3 • Lossless",
    art: "./assets/screen_player.png",
    format: "Opus 160 kbps"
  },
  {
    title: "Dual-Pane Reflections",
    artist: "InnerTube Client • Lossless",
    art: "./assets/screen_home.png",
    format: "FLAC 24-Bit"
  }
];

export const Hero: React.FC<HeroProps> = ({ onOpenUpi, isAudioPlaying, onToggleAudio }) => {
  const [songIdx, setSongIdx] = useState(0);
  const [isLiked, setIsLiked] = useState(false);
  const [progress, setProgress] = useState(42);
  const [eqHeights, setEqHeights] = useState<number[]>([12, 24, 18, 30, 16, 28, 14, 26, 20, 10]);

  const currentSong = DEMO_SONGS[songIdx];

  // Animate Equalizer bars when audio is active
  useEffect(() => {
    let interval: any;
    if (isAudioPlaying) {
      interval = setInterval(() => {
        setEqHeights(Array.from({ length: 10 }, () => Math.floor(Math.random() * 26) + 8));
      }, 90);
    } else {
      setEqHeights([10, 12, 10, 14, 10, 12, 10, 14, 10, 8]);
    }
    return () => clearInterval(interval);
  }, [isAudioPlaying]);

  const nextSong = (direction: number) => {
    setSongIdx((prev) => (prev + direction + DEMO_SONGS.length) % DEMO_SONGS.length);
  };

  const handleSeek = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const pct = Math.max(0, Math.min(100, ((e.clientX - rect.left) / rect.width) * 100));
    setProgress(pct);
  };

  return (
    <section id="top" className="relative pt-36 pb-20 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto flex flex-col items-center text-center">
      
      {/* Version Tag */}
      <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full liquid-glass-pill text-xs font-medium text-slate-300 mb-6 border border-white/10 shadow-lg">
        <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
        <span className="font-mono text-cyan-300 font-semibold">Pulse Wave v6.1.19 Released</span>
        <span className="text-slate-600">•</span>
        <span className="text-slate-400">Android 8.0+ Ready</span>
        <span className="text-slate-600">•</span>
        <span className="text-purple-300 font-mono">GPL-3.0</span>
      </div>

      {/* Main Headline */}
      <h1 className="text-5xl sm:text-7xl lg:text-8xl font-black tracking-tight title-gradient max-w-5xl leading-[1.05] mb-6">
        Sound sculpted in <br />
        <span className="accent-gradient">liquid glass.</span>
      </h1>

      {/* Subtitle */}
      <p className="text-lg sm:text-xl text-slate-400 max-w-2xl font-normal leading-relaxed mb-10">
        Pulse Wave is an open-source, ad-free music player for Android streaming the entire YouTube Music catalog on a Media3 ExoPlayer core with real-time backdrop blur refraction.
      </p>

      {/* Primary Action Buttons */}
      <div className="flex flex-wrap items-center justify-center gap-4 mb-16">
        <a
          href="https://github.com/BSFrameworks5253/Plusewave/releases/latest"
          target="_blank"
          rel="noreferrer"
          className="btn-primary-gradient px-8 py-4 rounded-2xl text-base font-bold text-white flex items-center gap-3 shadow-2xl cursor-pointer"
        >
          <Download className="w-5 h-5" />
          <span>Download Latest APK</span>
        </a>

        <button
          onClick={onOpenUpi}
          className="liquid-glass liquid-glass-hover px-6 py-4 rounded-2xl text-base font-semibold text-slate-200 hover:text-white flex items-center gap-2.5 cursor-pointer"
        >
          <span>💸 Sponsor via UPI</span>
        </button>

        <a
          href="https://github.com/BSFrameworks5253/Plusewave"
          target="_blank"
          rel="noreferrer"
          className="liquid-glass liquid-glass-hover px-6 py-4 rounded-2xl text-base font-semibold text-slate-300 hover:text-white flex items-center gap-2 cursor-pointer"
        >
          <ExternalLink className="w-5 h-5 text-slate-400" />
          <span>Source Code</span>
        </a>
      </div>

      {/* Center Flagship Phone Showcase */}
      <div className="relative w-full max-w-4xl mx-auto flex justify-center items-center">
        
        {/* Dynamic Glow Aura */}
        <div className="absolute w-[380px] h-[380px] rounded-full bg-gradient-to-tr from-purple-600/30 via-cyan-500/25 to-pink-500/20 filter blur-3xl pointer-events-none transition-all duration-700"></div>

        {/* Modern Flagship Phone Chassis */}
        <div className="w-[320px] h-[650px] rounded-[48px] bg-[#090b14] border-4 border-[#1f2333] shadow-2xl relative overflow-hidden flex flex-col justify-between p-3 transition-transform duration-300 hover:-translate-y-1">
          
          {/* Dynamic Island */}
          <div className="w-24 h-6 bg-black rounded-full mx-auto absolute top-4 left-1/2 -translate-x-1/2 z-40 border border-white/10 flex items-center justify-between px-3">
            <div className="w-2 h-2 rounded-full bg-cyan-400 animate-pulse"></div>
            <div className="w-2 h-2 rounded-full bg-purple-500"></div>
          </div>

          {/* Inner Phone Screen */}
          <div className="w-full h-full rounded-[38px] bg-gradient-to-b from-slate-900 via-[#0a0d18] to-slate-950 p-4 pt-10 flex flex-col justify-between overflow-hidden relative border border-white/10">
            
            {/* Top Bar inside Phone */}
            <div className="flex items-center justify-between text-xs text-slate-300 z-10 pt-1">
              <span className="font-mono text-[10px] text-cyan-400 font-semibold tracking-wider uppercase">PULSE WAVE V6</span>
              <span className="text-[10px] font-mono text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/30">
                {currentSong.format}
              </span>
            </div>

            {/* Rotating Vinyl & Album Cover */}
            <div className="relative my-3 flex items-center justify-center z-10">
              <div className={`w-48 h-48 rounded-full bg-[#12141c] border-2 border-white/10 shadow-2xl flex items-center justify-center ${isAudioPlaying ? 'animate-spin-slow' : ''} relative`}>
                <div className="w-24 h-24 rounded-full overflow-hidden border-4 border-slate-900 shadow-inner">
                  <img src={currentSong.art} alt={currentSong.title} className="w-full h-full object-cover" />
                </div>
                <div className="absolute w-4 h-4 rounded-full bg-black border border-white/30"></div>
              </div>
            </div>

            {/* Track Info & Like */}
            <div className="z-10 flex items-center justify-between px-1">
              <div className="text-left">
                <h3 className="text-base font-bold text-white tracking-tight truncate max-w-[210px]">{currentSong.title}</h3>
                <p className="text-xs text-purple-300 font-medium">{currentSong.artist}</p>
              </div>
              <button
                onClick={() => setIsLiked(!isLiked)}
                className="w-8 h-8 rounded-full liquid-glass flex items-center justify-center text-pink-400 active:scale-125 transition-transform cursor-pointer"
              >
                <Heart className={`w-4 h-4 ${isLiked ? 'fill-pink-500 text-pink-500' : ''}`} />
              </button>
            </div>

            {/* Equalizer Live Waveform */}
            <div className="z-10 flex items-end justify-between gap-1 h-8 px-1">
              {eqHeights.map((h, i) => (
                <div
                  key={i}
                  className="flex-1 rounded-full bg-gradient-to-t from-cyan-400 to-purple-500 transition-all duration-75"
                  style={{ height: `${h}px` }}
                />
              ))}
            </div>

            {/* Progress Bar */}
            <div className="z-10 space-y-1">
              <div className="w-full h-1.5 bg-white/10 rounded-full overflow-hidden cursor-pointer" onClick={handleSeek}>
                <div
                  className="h-full bg-gradient-to-r from-cyan-400 to-purple-500 transition-all"
                  style={{ width: `${progress}%` }}
                />
              </div>
              <div className="flex justify-between text-[10px] font-mono text-slate-400">
                <span>01:38</span>
                <span>03:42</span>
              </div>
            </div>

            {/* Controls */}
            <div className="z-10 flex items-center justify-between px-2 pb-1">
              <button
                onClick={() => nextSong(-1)}
                className="w-9 h-9 rounded-full liquid-glass flex items-center justify-center text-slate-300 hover:text-white active:scale-90 transition-transform cursor-pointer"
              >
                <SkipBack className="w-4 h-4 fill-current" />
              </button>

              <button
                onClick={onToggleAudio}
                className="w-13 h-13 p-3 rounded-full btn-primary-gradient flex items-center justify-center text-white shadow-xl hover:scale-105 active:scale-95 transition-all cursor-pointer"
              >
                {isAudioPlaying ? (
                  <Pause className="w-6 h-6 fill-current" />
                ) : (
                  <Play className="w-6 h-6 fill-current ml-0.5" />
                )}
              </button>

              <button
                onClick={() => nextSong(1)}
                className="w-9 h-9 rounded-full liquid-glass flex items-center justify-center text-slate-300 hover:text-white active:scale-90 transition-transform cursor-pointer"
              >
                <SkipForward className="w-4 h-4 fill-current" />
              </button>
            </div>

          </div>
        </div>

      </div>

    </section>
  );
};
