// JSON source context/rawJSON are required for lossless int64 on modern runtimes.
interface JSON {
  rawJSON(value: string): object;
  parse(text: string, reviver: (key: string, value: any, context?: { source?: string }) => any): any;
}
