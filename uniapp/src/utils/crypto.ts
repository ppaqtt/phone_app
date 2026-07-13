const SALT = 'qingjian_notes_salt_2024'

const simpleHash = (input: string): string => {
  let hash = 0
  for (let i = 0; i < input.length; i++) {
    const char = input.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash
  }
  return Math.abs(hash).toString(16).padStart(8, '0')
}

export const hashPin = (pin: string): string => {
  const salted = SALT + pin + SALT.split('').reverse().join('')
  let result = ''
  for (let i = 0; i < 5; i++) {
    result = simpleHash(result + salted + i)
  }
  return `v1:${result}`
}

export const verifyPin = (pin: string, storedHash: string): boolean => {
  if (!storedHash) return false
  if (storedHash.startsWith('v1:')) {
    return hashPin(pin) === storedHash
  }
  return pin === storedHash
}

export const isPinHashed = (storedPin: string): boolean => {
  return storedPin?.startsWith('v1:')
}
