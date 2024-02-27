/**
 * NOTE: The following code has not implemented the WEB standard correctly.
 * It only works for the OpenAI JS SDK.
 */

class Request {
  constructor(init) {
    Object.assign(this, init);
  }
}

class Response {
  constructor(init) {
    Object.assign(this, init);
  }

  async text() {
    if (this._text != null) {
      return this._text;
    }
    const buffer = await this._readAllBody();
    const text = _decodeTextUtf8(buffer);
    this._text = text;
    return text;
  }

  async json() {
    return JSON.parse(await this.text());
  }

  async _readAllBody() {
    const chunks = [];
    for await (const chunk of this.body) {
      chunks.push(chunk);
    }
    const bufferSize = chunks.reduce((prev, curr) => {
      return prev + curr.length;
    }, 0);
    const buffer = new Uint8Array(bufferSize);
    let index = 0;
    for (const chunk of chunks) {
      buffer.set(chunk, index);
      index += chunk.length;
    }
    return buffer;
  }
}

class Headers {
  constructor(headersInit) {
    this._index = 0;
    if (headersInit != null) {
      const entries = [];
      const keys = Object.keys(headersInit);
      for (let i = keys.length - 1; i >= 0; i--) {
        const key = keys[i];
        if (typeof key !== "string") continue;
        const lowerKey = key.toLowerCase();
        const value = headersInit[key];
        if (Array.isArray(value)) {
          if (value.length === 1) {
            entries.push([lowerKey, value[0].toString()]);
          } else if (value.length > 1) {
            entries.push([lowerKey, value.join(",")]);
          }
        } else if (typeof value === "string") {
          entries.push([lowerKey, value]);
        }
      }
      this._entries = entries;
    } else {
      this._entries = [];
    }
  }

  get(key) {
    if (key == null) {
      return undefined;
    }
    const lowerKey = key.toLowerCase();
    for (const pair of this._entries) {
      if (pair[0] === lowerKey) {
        return pair[1];
      }
    }
    return undefined;
  }

  [Symbol.iterator]() {
    const outer = this;
    return {
      next: () => {
        const entriesArray = outer._entries;
        if (outer._index < entriesArray.length) {
          return {
            value: entriesArray[outer._index++],
            done: false,
          };
        } else {
          return {
            done: true,
          };
        }
      },
    };
  }

  entries() {
    this._index = 0;
    return this;
  }
}

class URL {
  constructor(url) {
    this.protocol = url.split("//")[0];

    this.host = url.substring(this.protocol.length + 2).split("/")[0];
    const hostParts = this.host.split(":");
    this.hostname = hostParts[0];
    this.port = hostParts.length > 1 ? hostParts[1] : "";

    this.origin = this.protocol + "//" + this.host;

    const pathParts = url
      .substring(this.protocol.length + 2 + this.host.length)
      .split("?");
    this.pathname = pathParts[0];
    this.search = pathParts.length > 1 ? "?" + pathParts[1] : "";
  }

  toString() {
    return this.protocol + "//" + this.host + this.pathname + this.search;
  }
}

async function fetch(url, init) {
  const response = await _fetchInternal(url, init);
  const res = new Response({
    ...response,
    headers: new Headers(response.headers),
    body: new ReadableStream(response.bodyChannelId),
  });
  return res;
}

class ReadableStream {
  constructor(bodyChannelId) {
    this._reader = new ReadableStreamDefaultReader(bodyChannelId);
  }

  getReader() {
    return this._reader;
  }

  [Symbol.asyncIterator]() {
    return {
      next: this._reader.read.bind(this._reader),
    };
  }

  async return() {
    this._reader.releaseLock();
    return { done: true };
  }
}

class ReadableStreamDefaultReader {
  constructor(bodyChannelId) {
    this.bodyChannelId = bodyChannelId;
  }

  async cancel() {}

  async read() {
    const bytes = await _readFromResponseChannel(this.bodyChannelId);
    if (bytes != null) {
      return {
        value: bytes,
        done: false,
      };
    } else {
      return {
        done: true,
      };
    }
  }

  async releaseLock() {
  }

  [Symbol.asyncIterator]() {
    return {
      next: this.read.bind(this),
    };
  }
}

class AbortController {
  constructor() {
    this.signal = 0;
  }

  abort() {} // TODO: Implement
}

class TextDecoder {
  constructor() {}

  decode(bytes) {
    return _decodeTextUtf8(bytes);
  }
}
