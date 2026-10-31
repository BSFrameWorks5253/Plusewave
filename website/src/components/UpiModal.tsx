import React, { useState } from 'react';
import { X, Check, Copy } from 'lucide-react';

interface UpiModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const UpiModal: React.FC<UpiModalProps> = ({ isOpen, onClose }) => {
  const [copied, setCopied] = useState(false);
  const upiId = "akshanshsinha67@axl";

  if (!isOpen) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(upiId).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-xl flex items-center justify-center p-4 animate-fadeIn">
      <div className="liquid-glass rounded-3xl p-6 sm:p-8 max-w-sm w-full border border-purple-500/30 shadow-2xl relative space-y-5 text-center">
        
        <button
          onClick={onClose}
          className="absolute top-4 right-4 w-8 h-8 rounded-full liquid-glass flex items-center justify-center text-slate-400 hover:text-white cursor-pointer"
        >
          <X className="w-4 h-4" />
        </button>
        
        <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-cyan-500 to-purple-600 flex items-center justify-center mx-auto text-xl shadow-lg shadow-purple-500/30">
          💸
        </div>
        
        <div className="space-y-1">
          <h3 className="text-xl font-bold text-white">Sponsor Pulse Wave</h3>
          <p className="text-xs text-slate-300">Scan via Google Pay, PhonePe, Paytm or BHIM</p>
        </div>

        {/* High-res generated QR Code */}
        <div className="bg-white p-3 rounded-2xl w-44 h-44 mx-auto flex items-center justify-center shadow-inner">
          <img
            src={`https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=upi://pay?pa=${encodeURIComponent(upiId)}%26pn=PulseWave%26cu=INR`}
            alt="UPI QR Code"
            className="w-full h-full object-contain"
          />
        </div>

        {/* Copy UPI Address Box */}
        <div className="flex items-center gap-2 bg-slate-900/90 p-2 rounded-xl border border-white/10">
          <code className="text-xs font-mono text-cyan-300 flex-1 truncate px-1">{upiId}</code>
          <button
            onClick={handleCopy}
            className="btn-primary-gradient px-3 py-1.5 rounded-lg text-xs font-bold text-white flex items-center gap-1 cursor-pointer"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-white" />
                <span>Copied</span>
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" />
                <span>Copy</span>
              </>
            )}
          </button>
        </div>

        <p className="text-[11px] text-slate-400">
          Every contribution keeps servers running and fuels open-source feature development. Thank you! ❤️
        </p>

      </div>
    </div>
  );
};
