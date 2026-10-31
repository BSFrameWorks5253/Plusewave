import React, { useState, useRef, useEffect } from 'react';
import { Navbar } from './components/Navbar';
import { Hero } from './components/Hero';
import { Metrics } from './components/Metrics';
import { LiquidGlassSection } from './components/LiquidGlassSection';
import { StreamingSection } from './components/StreamingSection';
import { LyricsSection } from './components/LyricsSection';
import { AdaptiveSection } from './components/AdaptiveSection';
import { PrivacySection } from './components/PrivacySection';
import { ArchitectureSection } from './components/ArchitectureSection';
import { DownloadSection } from './components/DownloadSection';
import { Footer } from './components/Footer';
import { UpiModal } from './components/UpiModal';

export const App: React.FC = () => {
  const [isSynthPlaying, setIsSynthPlaying] = useState(false);
  const [isUpiModalOpen, setIsUpiModalOpen] = useState(false);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const synthIntervalRef = useRef<any>(null);
  const noteIdxRef = useRef(0);

  // Web Audio Procedural Synthesizer
  const noteFrequencies = [220, 261.63, 329.63, 392, 440, 523.25];

  const playSynthNote = () => {
    if (!audioCtxRef.current || !isSynthPlaying) return;
    try {
      const ctx = audioCtxRef.current;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      const filter = ctx.createBiquadFilter();

      const freq = noteFrequencies[noteIdxRef.current % noteFrequencies.length];
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(freq, ctx.currentTime);

      filter.type = 'lowpass';
      filter.frequency.setValueAtTime(650, ctx.currentTime);
      filter.frequency.exponentialRampToValueAtTime(1400, ctx.currentTime + 0.6);

      gain.gain.setValueAtTime(0.02, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 1.2);

      osc.connect(filter);
      filter.connect(gain);
      gain.connect(ctx.destination);

      osc.start();
      osc.stop(ctx.currentTime + 1.2);

      noteIdxRef.current += 1;
    } catch (e) {
      console.warn("Audio Context note error:", e);
    }
  };

  const handleToggleSynth = () => {
    if (!audioCtxRef.current) {
      const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
      audioCtxRef.current = new AudioContextClass();
    }
    if (audioCtxRef.current.state === 'suspended') {
      audioCtxRef.current.resume();
    }

    setIsSynthPlaying((prev) => !prev);
  };

  useEffect(() => {
    if (isSynthPlaying) {
      playSynthNote();
      synthIntervalRef.current = setInterval(playSynthNote, 1300);
    } else {
      if (synthIntervalRef.current) clearInterval(synthIntervalRef.current);
    }
    return () => {
      if (synthIntervalRef.current) clearInterval(synthIntervalRef.current);
    };
  }, [isSynthPlaying]);

  return (
    <div className="min-h-screen bg-[#05060a] text-slate-100 relative selection:bg-purple-600 selection:text-white">
      {/* Ambient Lighting Spheres */}
      <div className="aurora-mesh-1"></div>
      <div className="aurora-mesh-2"></div>

      {/* Sticky Liquid Glass Navbar */}
      <Navbar
        isSynthPlaying={isSynthPlaying}
        onToggleSynth={handleToggleSynth}
      />

      {/* Main Content Sections */}
      <main>
        <Hero
          onOpenUpi={() => setIsUpiModalOpen(true)}
          isAudioPlaying={isSynthPlaying}
          onToggleAudio={handleToggleSynth}
        />
        <Metrics />
        <LiquidGlassSection />
        <StreamingSection />
        <LyricsSection />
        <AdaptiveSection />
        <PrivacySection />
        <ArchitectureSection />
        <DownloadSection onOpenUpi={() => setIsUpiModalOpen(true)} />
      </main>

      {/* Footer */}
      <Footer />

      {/* UPI Sponsor Dialog */}
      <UpiModal
        isOpen={isUpiModalOpen}
        onClose={() => setIsUpiModalOpen(false)}
      />
    </div>
  );
};

export default App;
