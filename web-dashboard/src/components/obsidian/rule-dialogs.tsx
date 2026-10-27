"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useLive } from "@/components/shell/live-provider";
import { api } from "@/lib/api";
import { indicatorTypeLabel } from "@/lib/format";
import { isNetworkIndicator, normalizeIndicator, validateIndicator } from "@/lib/indicators";
import type { IndicatorType, ListType, ThreatCategory, ThreatRule } from "@/lib/types";
import { ContextNotice, MetaList } from "./panel";

/** TODO.md §6: enterprise user bypasses need a TTL. The TTL picker itself is [P1]. */
export const ENTERPRISE_BYPASS_TTL_MINUTES = 15;

export interface RuleChange {
  action: "report" | "bypass";
  indicatorType: IndicatorType;
  indicatorValue: string;
  /** The list the indicator is on now, if any. */
  currentList: ListType | null;
  threatCategory?: ThreatCategory;
}

const listLabel = (list: ListType | null) => (list === "BLACKLIST" ? "Blacklist" : list === "WHITELIST" ? "Whitelist" : "No rule");

/** Sensitive-change confirmation: before → after, expected effect, then confirm. */
export function ConfirmRuleChange({
  change,
  onOpenChange,
  onDone,
}: {
  change: RuleChange | null;
  onOpenChange: (open: boolean) => void;
  onDone?: (rule: ThreatRule) => void;
}) {
  const { session } = useLive();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!change) return null;
  const report = change.action === "report";
  const network = isNetworkIndicator(change.indicatorType);
  const timeBound = !report && session?.role === "EMPLOYEE";
  const audience = session?.organization ? `everyone in ${session.organization.name}` : "your account";

  const effect = report
    ? network
      ? `Connected agents add it to the OBSIDIAN iptables chain and the /etc/hosts sinkhole within a second. This protects ${audience}.`
      : `Email and message scans flag it as malicious for ${audience}.`
    : network
      ? "Connected agents lift the block and traffic to it is allowed again."
      : "Email and message scans stop flagging it.";

  const confirm = async () => {
    setPending(true);
    setError(null);
    try {
      const rule = report
        ? await api.report({
            indicatorType: change.indicatorType,
            indicatorValue: change.indicatorValue,
            threatCategory: change.threatCategory,
          })
        : await api.bypass({
            indicatorType: change.indicatorType,
            indicatorValue: change.indicatorValue,
            ttlMinutes: timeBound ? ENTERPRISE_BYPASS_TTL_MINUTES : undefined,
          });
      onDone?.(rule);
      onOpenChange(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed.");
    } finally {
      setPending(false);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !pending && onOpenChange(open)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{report ? "Report to blacklist?" : "Bypass to whitelist?"}</DialogTitle>
          <DialogDescription className="font-mono break-all text-ink">{change.indicatorValue}</DialogDescription>
        </DialogHeader>
        <MetaList
          items={[
            ["Indicator", indicatorTypeLabel[change.indicatorType]],
            ["Change", `${listLabel(change.currentList)} → ${report ? "Blacklist" : "Whitelist"}`],
            ...(timeBound ? ([["Expires", `After ${ENTERPRISE_BYPASS_TTL_MINUTES} minutes`]] as [string, string][]) : []),
          ]}
        />
        <p className="text-[12px] leading-[1.4] text-subtle">{effect}</p>
        {error ? (
          <ContextNotice tone="critical" title="Not applied">
            {error.slice(0, 200)} The rule list is unchanged.
          </ContextNotice>
        ) : null}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
            Cancel
          </Button>
          <Button onClick={confirm} disabled={pending}>
            {pending ? "Applying…" : report ? "Report to blacklist" : "Bypass to whitelist"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

const INDICATOR_TYPES: IndicatorType[] = ["DOMAIN", "IP", "URL", "EMAIL", "PHONE", "FILE_HASH"];
const CATEGORIES: ThreatCategory[] = ["PHISHING", "MALWARE", "SPAM", "MALICIOUS"];
const placeholders: Record<IndicatorType, string> = {
  DOMAIN: "login-verify.example",
  IP: "203.0.113.45",
  URL: "https://login-verify.example/reset",
  EMAIL: "billing@invoice-verify.example",
  PHONE: "+15550100",
  FILE_HASH: "64 hexadecimal characters",
};

/** "New rule": collects an indicator, then hands off to ConfirmRuleChange. */
export function NewRuleDialog({
  open,
  onOpenChange,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (change: RuleChange) => void;
}) {
  const [type, setType] = useState<IndicatorType>("DOMAIN");
  const [value, setValue] = useState("");
  const [list, setList] = useState<ListType>("BLACKLIST");
  const [category, setCategory] = useState<ThreatCategory>("PHISHING");
  const [touched, setTouched] = useState(false);
  const error = touched ? validateIndicator(type, value) : null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form
          className="grid gap-4"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            setTouched(true);
            if (validateIndicator(type, value)) return;
            onSubmit({
              action: list === "BLACKLIST" ? "report" : "bypass",
              indicatorType: type,
              indicatorValue: normalizeIndicator(type, value),
              currentList: null,
              threatCategory: list === "BLACKLIST" ? category : undefined,
            });
            setValue("");
            setTouched(false);
          }}
        >
          <DialogHeader>
            <DialogTitle>New rule</DialogTitle>
            <DialogDescription>
              Values are stored lowercase. Domains and IP addresses also reach connected agents.
            </DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-[7px]">
              <Label htmlFor="rule-type">Indicator type</Label>
              <Select value={type} onValueChange={(v) => setType(v as IndicatorType)}>
                <SelectTrigger id="rule-type" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {INDICATOR_TYPES.map((t) => (
                    <SelectItem key={t} value={t}>
                      {indicatorTypeLabel[t]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-[7px]">
              <Label htmlFor="rule-list">List</Label>
              <Select value={list} onValueChange={(v) => setList(v as ListType)}>
                <SelectTrigger id="rule-list" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="BLACKLIST">Blacklist · block</SelectItem>
                  <SelectItem value="WHITELIST">Whitelist · allow</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="grid gap-[7px]">
            <Label htmlFor="rule-value">Value · required</Label>
            <Input
              id="rule-value"
              value={value}
              autoComplete="off"
              spellCheck={false}
              placeholder={placeholders[type]}
              aria-invalid={error ? true : undefined}
              aria-describedby="rule-value-help"
              onChange={(event) => setValue(event.target.value)}
              onBlur={() => setTouched(true)}
              className="font-mono"
            />
            <p id="rule-value-help" className={error ? "text-[11px] text-critical" : "text-[11px] text-subtle"}>
              {error ?? "Use the bare indicator. Subdomains are not covered by a domain rule."}
            </p>
          </div>
          {list === "BLACKLIST" ? (
            <div className="grid gap-[7px]">
              <Label htmlFor="rule-category">Category</Label>
              <Select value={category} onValueChange={(v) => setCategory(v as ThreatCategory)}>
                <SelectTrigger id="rule-category" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CATEGORIES.map((c) => (
                    <SelectItem key={c} value={c}>
                      {c.charAt(0) + c.slice(1).toLowerCase()}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit">Review change</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
